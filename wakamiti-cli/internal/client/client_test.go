/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package client

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

func TestDoPostJSONArgs_SendsJSONListBody(t *testing.T) {
	wantArgs := []string{"--foo", "bar", "--baz=1"}
	wantOrigin := "test-origin"
	client := &Client{
		Config: Config{Origin: wantOrigin},
	}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("method=%s want POST", r.Method)
		}
		ct := r.Header.Get("Content-Type")
		if !strings.HasPrefix(ct, "application/json") {
			t.Fatalf("Content-Type=%q want application/json", ct)
		}
		origin := r.Header.Get(HEADER)
		if origin != wantOrigin {
			t.Fatalf("Origin=%q want %q", origin, wantOrigin)
		}
		b, _ := io.ReadAll(r.Body)
		if string(b) != "[\"--foo\",\"bar\",\"--baz=1\"]" {
			t.Fatalf("body=%q want JSON array", string(b))
		}
		w.WriteHeader(202)
	}))
	defer srv.Close()

	if err := client.DoPostJSONArgs(context.Background(), srv.URL, wantArgs); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestDoPostJSONArgs_Non2xxFails(t *testing.T) {
	client := &Client{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusBadRequest)
	}))
	defer srv.Close()

	if client.DoPostJSONArgs(context.Background(), srv.URL, []string{"x"}) == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestDoPostJSONArgs_NetworkError(t *testing.T) {
	client := &Client{}
	if client.DoPostJSONArgs(context.Background(), "http://localhost:1", []string{"x"}) == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestStreamWS_ProgressThenCloseReasonExitCode(t *testing.T) {
	wantOrigin := "test-origin"
	client := &Client{
		Config: Config{Origin: wantOrigin},
	}
	wsURL := startWSServer(t, func(c *websocket.Conn, r *http.Request) {
		if r.Header.Get(HEADER) != wantOrigin {
			t.Errorf("Missing or incorrect %s header", HEADER)
		}
		_ = c.WriteMessage(websocket.TextMessage, []byte("10%"))
		_ = c.WriteMessage(websocket.TextMessage, []byte("50%"))

		// Controlled close with reason "0" (command exit code).
		_ = c.WriteControl(
			websocket.CloseMessage,
			websocket.FormatCloseMessage(websocket.CloseNormalClosure, "0"),
			time.Now().Add(1*time.Second),
		)
	})

	// Capture stdout to assert streamed progress.
	oldStdout := os.Stdout
	r, w, _ := os.Pipe()
	os.Stdout = w

	ctx := context.Background()
	code, err := client.StreamWS(ctx, wsURL)

	_ = w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if code != 0 {
		t.Fatalf("exitCode=%d want 0", code)
	}

	out, _ := io.ReadAll(r)
	s := string(out)
	if !strings.Contains(s, "10%") || !strings.Contains(s, "50%") {
		t.Fatalf("stdout=%q want progress lines", s)
	}
}

func TestStreamWS_CloseReasonErrorText_ReturnsError(t *testing.T) {
	client := &Client{}
	wsURL := startWSServer(t, func(c *websocket.Conn, r *http.Request) {
		_ = c.WriteMessage(websocket.TextMessage, []byte("working..."))
		_ = c.WriteControl(
			websocket.CloseMessage,
			websocket.FormatCloseMessage(websocket.CloseInternalServerErr, "NullPointerException"),
			time.Now().Add(1*time.Second),
		)
	})

	ctx := context.Background()
	code, err := client.StreamWS(ctx, wsURL)

	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if code != 1 {
		t.Fatalf("exitCode=%d want 1", code)
	}
	if !strings.Contains(err.Error(), "NullPointerException") {
		t.Fatalf("err=%q want to contain exception text", err.Error())
	}
}

func TestStreamWS_CtrlC_SendsSTOP_ServerClosesWithExitCode(t *testing.T) {
	client := &Client{}
	var (
		mu      sync.Mutex
		gotSTOP bool
		ready   = make(chan struct{})
	)

	wsURL := startWSServer(t, func(c *websocket.Conn, r *http.Request) {
		close(ready)

		for {
			_, msg, err := c.ReadMessage()
			if err != nil {
				return
			}
			if strings.TrimSpace(string(msg)) == "STOP" {
				mu.Lock()
				gotSTOP = true
				mu.Unlock()

				// Server decides final command exit code using close reason "5".
				_ = c.WriteControl(
					websocket.CloseMessage,
					websocket.FormatCloseMessage(websocket.CloseNormalClosure, "5"),
					time.Now().Add(1*time.Second),
				)
				return
			}
		}
	})

	ctx, cancel := context.WithCancel(context.Background())

	done := make(chan struct{})
	var code int
	var err error
	go func() {
		code, err = client.StreamWS(ctx, wsURL)
		close(done)
	}()

	<-ready
	cancel()

	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("timeout waiting streamWS")
	}

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if code != 5 {
		t.Fatalf("exitCode=%d want 5", code)
	}

	mu.Lock()
	defer mu.Unlock()
	if !gotSTOP {
		t.Fatal("expected STOP to be sent")
	}
}

func TestStreamWS_DialError(t *testing.T) {
	client := &Client{}
	ctx := context.Background()
	_, err := client.StreamWS(ctx, "ws://localhost:1")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestHandleServerClose_NotCloseError(t *testing.T) {
	client := &Client{}
	err := io.EOF
	code, gotErr := client.HandleServerClose(err)
	if code != 1 {
		t.Fatalf("code=%d want 1", code)
	}
	if gotErr != err {
		t.Fatalf("err=%v want %v", gotErr, err)
	}
}

func TestHandleServerClose_EmptyReason(t *testing.T) {
	client := &Client{}
	err := &websocket.CloseError{Code: websocket.CloseNormalClosure, Text: ""}
	code, gotErr := client.HandleServerClose(err)
	if code != 1 {
		t.Fatalf("code=%d want 1", code)
	}
	if !strings.Contains(gotErr.Error(), "unknown reason") {
		t.Fatalf("err=%v want unknown reason", gotErr)
	}
}

func TestStreamWS_ReadError(t *testing.T) {
	client := &Client{}
	wsURL := startWSServer(t, func(c *websocket.Conn, r *http.Request) {
		// Just close immediately without a proper CloseMessage
		c.Close()
	})

	ctx := context.Background()
	_, err := client.StreamWS(ctx, wsURL)
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestRun_Success(t *testing.T) {
	upgrader := websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool { return true }, // Test server accepts all origins.
	}
	wantOrigin := "test-origin"
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get(HEADER) != wantOrigin {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		if r.URL.Path == "/exec/out" {
			if r.Header.Get("Upgrade") == "websocket" {
				c, err := upgrader.Upgrade(w, r, nil)
				if err != nil {
					return
				}
				defer c.Close()
				_ = c.WriteMessage(websocket.TextMessage, []byte("progress"))
				// Read STOP message sent after context cancellation.
				_, msg, _ := c.ReadMessage()
				if string(msg) == "STOP" {
					_ = c.WriteControl(
						websocket.CloseMessage,
						websocket.FormatCloseMessage(websocket.CloseNormalClosure, "0"),
						time.Now().Add(1*time.Second),
					)
				}
			} else {
				w.WriteHeader(http.StatusAccepted)
			}
		} else {
			w.WriteHeader(http.StatusAccepted)
		}
	})

	srv := httptest.NewServer(handler)
	defer srv.Close()

	hostPort := strings.TrimPrefix(srv.URL, "http://")
	parts := strings.Split(hostPort, ":")

	client := &Client{
		Config: Config{
			ServiceHost: parts[0],
			ServicePort: parts[1],
			Origin:      wantOrigin,
		},
	}

	// Capture stderr to keep test output clean.
	oldStderr := os.Stderr
	_, wErr, _ := os.Pipe()
	os.Stderr = wErr

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(100 * time.Millisecond)
		cancel()
	}()

	code := client.Run(ctx, []string{"arg1", "arg2"})

	_ = wErr.Close()
	os.Stderr = oldStderr

	if code != 0 {
		t.Fatalf("code=%d want 0", code)
	}
}

func TestRun_PostError(t *testing.T) {
	client := &Client{
		Config: Config{
			ServiceHost: "localhost",
			ServicePort: "1",
		},
	}

	oldStderr := os.Stderr
	_, wErr, _ := os.Pipe()
	os.Stderr = wErr

	code := client.Run(context.Background(), nil)

	_ = wErr.Close()
	os.Stderr = oldStderr

	if code != 255 {
		t.Fatalf("code=%d want 255", code)
	}
}

func TestRun_StreamError(t *testing.T) {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			w.WriteHeader(http.StatusAccepted)
		} else {
			// Force WebSocket handshake failure.
			http.Error(w, "no ws", http.StatusBadRequest)
		}
	})
	srv := httptest.NewServer(handler)
	defer srv.Close()

	hostPort := strings.TrimPrefix(srv.URL, "http://")
	parts := strings.Split(hostPort, ":")

	client := &Client{
		Config: Config{
			ServiceHost: parts[0],
			ServicePort: parts[1],
		},
	}

	oldStderr := os.Stderr
	_, wErr, _ := os.Pipe()
	os.Stderr = wErr

	code := client.Run(context.Background(), nil)

	_ = wErr.Close()
	os.Stderr = oldStderr

	if code != 3 {
		t.Fatalf("code=%d want 3", code)
	}
}

// startWSServer creates a throwaway websocket server and returns ws:// URL.

func startWSServer(t *testing.T, handler func(*websocket.Conn, *http.Request)) string {
	t.Helper()

	up := websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool { return true },
	}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		c, err := up.Upgrade(w, r, nil)
		if err != nil {
			t.Fatalf("upgrade error: %v", err)
		}
		defer c.Close()
		handler(c, r)
	}))
	t.Cleanup(srv.Close)

	return "ws" + strings.TrimPrefix(srv.URL, "http")
}
