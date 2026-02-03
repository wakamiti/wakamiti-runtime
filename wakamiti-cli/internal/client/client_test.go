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

	"es.wakamiti/wakamiti-cli/internal/config"
	"github.com/gorilla/websocket"
)

func TestDoPostText_SendsPlainTextBody(t *testing.T) {
	wantBody := "--foo bar --baz=1"
	client := &Client{}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("method=%s want POST", r.Method)
		}
		ct := r.Header.Get("Content-Type")
		if !strings.HasPrefix(ct, "text/plain") {
			t.Fatalf("Content-Type=%q want text/plain", ct)
		}
		b, _ := io.ReadAll(r.Body)
		if string(b) != wantBody {
			t.Fatalf("body=%q want %q", string(b), wantBody)
		}
		w.WriteHeader(202)
	}))
	defer srv.Close()

	if err := client.DoPostText(context.Background(), srv.URL, wantBody); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestDoPostText_Non2xxFails(t *testing.T) {
	client := &Client{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusBadRequest)
	}))
	defer srv.Close()

	if client.DoPostText(context.Background(), srv.URL, "x") == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestDoPostText_NetworkError(t *testing.T) {
	client := &Client{}
	if client.DoPostText(context.Background(), "http://localhost:1", "x") == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestStreamWS_ProgressThenCloseReasonExitCode(t *testing.T) {
	client := &Client{}
	wsURL := startWSServer(t, func(c *websocket.Conn) {
		_ = c.WriteMessage(websocket.TextMessage, []byte("10%"))
		_ = c.WriteMessage(websocket.TextMessage, []byte("50%"))

		// cierre controlado con reason="0" (exit code)
		_ = c.WriteControl(
			websocket.CloseMessage,
			websocket.FormatCloseMessage(websocket.CloseNormalClosure, "0"),
			time.Now().Add(1*time.Second),
		)
	})

	// Capturar stdout
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
	wsURL := startWSServer(t, func(c *websocket.Conn) {
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

	wsURL := startWSServer(t, func(c *websocket.Conn) {
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

				// servidor decide el resultado: reason="5"
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
	wsURL := startWSServer(t, func(c *websocket.Conn) {
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
	upgrader := websocket.Upgrader{}
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/exec" {
			if r.Header.Get("Upgrade") == "websocket" {
				c, err := upgrader.Upgrade(w, r, nil)
				if err != nil {
					return
				}
				defer c.Close()
				_ = c.WriteMessage(websocket.TextMessage, []byte("progress"))
				// Read STOP message
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
		}
	})

	srv := httptest.NewServer(handler)
	defer srv.Close()

	hostPort := strings.TrimPrefix(srv.URL, "http://")
	parts := strings.Split(hostPort, ":")

	client := &Client{
		Config: config.Config{
			ServiceHost: parts[0],
			ServicePort: parts[1],
		},
	}

	// Capture stderr to avoid polluting test output and check for the stop message
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
		Config: config.Config{
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
			// Fail WS dial
			http.Error(w, "no ws", http.StatusBadRequest)
		}
	})
	srv := httptest.NewServer(handler)
	defer srv.Close()

	hostPort := strings.TrimPrefix(srv.URL, "http://")
	parts := strings.Split(hostPort, ":")

	client := &Client{
		Config: config.Config{
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

// ---- WS test server helper ----

func startWSServer(t *testing.T, handler func(*websocket.Conn)) string {
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
		handler(c)
	}))
	t.Cleanup(srv.Close)

	return "ws" + strings.TrimPrefix(srv.URL, "http")
}
