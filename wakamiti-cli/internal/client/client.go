/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package client

import (
	"bytes"
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

// Client handles communication with the Wakamiti service.
type Client struct {
	Config Config
}

// Run executes the CLI logic.
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

// DoPostText sends a POST request with plain text body to the specified URL.
func (c *Client) DoPostText(ctx context.Context, url, body string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBufferString(body))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "text/plain; charset=utf-8")

	httpClient := &http.Client{}
	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("service returned status %s: %s", resp.Status, strings.TrimSpace(string(respBody)))
	}
	return nil
}

// StreamWS connects to a WebSocket URL and prints received messages to stdout.
func (c *Client) StreamWS(ctx context.Context, wsURL string) (int, error) {
	d := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, _, err := d.DialContext(ctx, wsURL, nil)
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
		_ = conn.WriteMessage(websocket.TextMessage, []byte("STOP"))
		select {
		case res := <-resultChan:
			return res.exitCode, res.err
		case <-time.After(3 * time.Second):
			return 1, context.Canceled
		}
	}
}

// HandleServerClose interprets the WebSocket closure error to extract an exit code or error message.
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
