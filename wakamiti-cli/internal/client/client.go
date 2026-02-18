/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package client

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

const (
	// HEADER is the HTTP header used by the backend to authorize requests by origin.
	HEADER = "Origin"
	// defaultHTTPTimeout prevents hanging forever when the service is unreachable.
	defaultHTTPTimeout = 15 * time.Second
	// maxErrorBodyBytes avoids reading unbounded error bodies into memory.
	maxErrorBodyBytes = 4096
	// stopMessageWriteWait bounds how long we wait while trying to send STOP on cancellation.
	stopMessageWriteWait = 2 * time.Second
)

// Client handles all network communication with the Wakamiti service.
//
// It is intentionally small: it only knows how to:
// 1. submit a command through HTTP,
// 2. consume live logs through WebSocket,
// 3. translate close reasons into process exit codes.
type Client struct {
	Config Config
}

// Run orchestrates the full client workflow and returns the process exit code.
//
// Flow:
// 1. Build command text from CLI arguments.
// 2. POST the command to /exec.
// 3. Open WebSocket /exec/out and stream logs until the server closes.
//
// Exit code policy:
// - 255: command submission failed.
// - 3: stream-level/client-side failure.
// - N: server-provided command exit status.
//
// Example:
//
//	code := cli.Run(ctx, []string{"--env", "qa", "--tags", "@smoke"})
func (c *Client) Run(ctx context.Context, args []string) int {
	bodyTxt := strings.Join(args, " ")

	postURL := fmt.Sprintf("http://%s:%s/exec", c.Config.ServiceHost, c.Config.ServicePort)
	wsURL := fmt.Sprintf("ws://%s:%s/exec/out", c.Config.ServiceHost, c.Config.ServicePort)

	// 1) Asynchronous POST (we don't wait for result, but validate status)
	if err := c.DoPostText(ctx, postURL, bodyTxt); err != nil {
		if !errors.Is(err, context.Canceled) {
			fmt.Fprintf(os.Stderr, "Error starting execution: %v\n", err)
		}
		return 255
	}

	// 2) Connect to WS and stream progress.
	exitCode, err := c.StreamWS(ctx, wsURL)
	if err != nil {
		// A nil error with a non-zero exit code is a valid scenario (e.g., script returns non-zero).
		// Any other error should be printed, unless it's a context cancellation.
		if !errors.Is(err, context.Canceled) {
			fmt.Fprintf(os.Stderr, "Stream error: %v\n", err)
			return 3 // Return code 3 for stream errors, as expected by tests.
		}
	}
	return exitCode
}

// DoPostText sends the command as text/plain to the execution endpoint.
//
// Security and resilience notes:
// - it always sets the configured Origin header expected by the server filter,
// - it uses a bounded HTTP timeout,
// - on non-2xx responses, it reads only a limited amount of response body.
func (c *Client) DoPostText(ctx context.Context, url, body string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, strings.NewReader(body))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "text/plain; charset=utf-8")

	httpClient := &http.Client{Timeout: defaultHTTPTimeout}
	req.Header.Set(HEADER, c.Config.Origin)
	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(io.LimitReader(resp.Body, maxErrorBodyBytes))
		return fmt.Errorf("service returned status %s: %s", resp.Status, strings.TrimSpace(string(respBody)))
	}
	return nil
}

// StreamWS opens a WebSocket connection and streams server messages to stdout.
//
// Normal behavior:
// - each non-empty incoming text message is printed as one output line,
// - when the server closes the socket, the close reason is converted into an exit code.
//
// Cancellation behavior:
// - when ctx is canceled (Ctrl+C), the client sends "STOP" to the server,
// - it then waits briefly for a graceful close carrying the final exit code.
func (c *Client) StreamWS(ctx context.Context, wsURL string) (int, error) {
	d := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, _, err := d.DialContext(ctx, wsURL, http.Header{
		HEADER: []string{c.Config.Origin},
	})
	if err != nil {
		return 1, fmt.Errorf("failed to connect to WebSocket: %w", err)
	}
	defer conn.Close()

	resultChan := make(chan struct {
		exitCode int
		err      error
	}, 1)

	go func() {
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				exitCode, streamErr := c.HandleServerClose(err)
				resultChan <- struct {
					exitCode int
					err      error
				}{exitCode, streamErr}
				return
			}
			if text := strings.TrimSpace(string(msg)); text != "" {
				fmt.Println(text)
			}
		}
	}()

	select {
	case res := <-resultChan:
		return res.exitCode, res.err
	case <-ctx.Done():
		fmt.Fprintln(os.Stderr, "> Stop request sent. The application will stop when the server closes the session.")
		_ = conn.SetWriteDeadline(time.Now().Add(stopMessageWriteWait))
		_ = conn.WriteMessage(websocket.TextMessage, []byte("STOP"))
		_ = conn.SetWriteDeadline(time.Time{})
		select {
		case res := <-resultChan:
			return res.exitCode, res.err
		case <-time.After(3 * time.Second):
			return 1, context.Canceled
		}
	}
}

// HandleServerClose interprets WebSocket close errors into (exitCode, error).
//
// Convention used by the backend:
// - Close reason with an integer (e.g. "0", "5") means command exit code.
// - Non-numeric close reason means execution error message.
// - Empty reason is treated as unknown close cause.
func (c *Client) HandleServerClose(err error) (int, error) {
	var ce *websocket.CloseError
	if errors.As(err, &ce) {
		reason := strings.TrimSpace(ce.Text)
		if n, err := strconv.Atoi(reason); err == nil {
			return n, nil
		}
		if reason != "" {
			return 1, errors.New(reason)
		}
		// Restore original behavior for empty reason to match test expectation.
		return 1, errors.New("websocket closed by unknown reason")
	}
	return 1, err
}
