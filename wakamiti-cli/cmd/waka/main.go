/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"es.wakamiti/wakamiti-cli/internal/client"
)

// main is intentionally thin:
// - it wires OS signal cancellation,
// - delegates business flow to run,
// - exits using the resulting code.
func main() {
	// Set up context with signal notification.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	// The stop function must be called to release resources, and defer ensures it runs.
	defer stop()
	exitCode := run(ctx)
	os.Exit(exitCode)
}

// run loads configuration, builds the client, and executes command flow.
//
// A non-zero return means either:
// - configuration/bootstrap error,
// - server start/stream failure,
// - or command exit status propagated from backend.
func run(ctx context.Context) int {
	conf, err := client.NewConfig()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}

	cli := client.Client{Config: *conf}

	// Pass the cancellable context to the client.
	return cli.Run(ctx, os.Args[1:])
}
