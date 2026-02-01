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
	"sync"
	"time"

	"es.wakamiti/wakamiti-cli/internal/config"
	"github.com/gorilla/websocket"
)

// Client handles communication with the Wakamiti service.
type Client struct {
	Config config.Config
}

// Run executes the CLI logic.
func (c *Client) Run(ctx context.Context, args []string) int {
	bodyTxt := strings.Join(args, " ")

	postURL := fmt.Sprintf("http://%s:%s/exec", c.Config.ServiceHost, c.Config.ServicePort)
	wsURL := fmt.Sprintf("ws://%s:%s/exec", c.Config.ServiceHost, c.Config.ServicePort)

	// 1) Asynchronous POST (we don't wait for result, but validate status)
	if err := c.DoPostText(postURL, bodyTxt); err != nil {
		fmt.Fprintf(os.Stderr, "Error starting execution: %v\n", err)
		return 255
	}

	// 2) Connect to WS and stream progress.
	exitCode, err := c.StreamWS(ctx, wsURL)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Stream error: %v\n", err)
		return 3
	}
	return exitCode
}

// DoPostText sends a POST request with plain text body to the specified URL.
func (c *Client) DoPostText(url, body string) error {
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewBufferString(body))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "text/plain; charset=utf-8")

	httpClient := &http.Client{Timeout: 15 * time.Second}
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
// It returns the exit code provided by the server or an error.
func (c *Client) StreamWS(ctx context.Context, wsURL string) (int, error) {
	d := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, _, err := d.Dial(wsURL, nil)
	if err != nil {
		return 0, fmt.Errorf("failed to connect to WebSocket: %w", err)
	}
	defer conn.Close()

	// Handle graceful shutdown on context cancellation (e.g., Ctrl+C)
	var once sync.Once
	go func() {
		<-ctx.Done()
		once.Do(func() {
			fmt.Fprintln(os.Stderr, "> Stop request sent. The application will stop when the server closes the session.")
		})
		//_ = conn.SetWriteDeadline(time.Now().Add(2 * time.Second))
		_ = conn.WriteMessage(websocket.TextMessage, []byte("STOP"))
	}()

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			return c.HandleServerClose(err)
		}

		if text := strings.TrimSpace(string(msg)); text != "" {
			fmt.Println(text)
		}
	}
}

// HandleServerClose interprets the WebSocket closure error to extract an exit code or error message.
func (c *Client) HandleServerClose(err error) (int, error) {
	var ce *websocket.CloseError
	if !errors.As(err, &ce) {
		return 1, err
	}

	reason := strings.TrimSpace(ce.Text)

	// If the reason is numeric, use it as the exit code
	if n, err := strconv.Atoi(reason); err == nil {
		return n, nil
	}

	// If the reason is not numeric, treat it as an error message
	if reason == "" {
		reason = "websocket closed by unknown reason"
	}
	return 1, errors.New(reason)
}
