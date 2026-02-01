/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package main

import (
	"context"
	"flag"
	"os"
	"os/signal"
	"syscall"

	"es.wakamiti/wakamiti-cli/internal/client"
	"es.wakamiti/wakamiti-cli/internal/config"
)

func main() {
	conf := config.Config{
		ServiceHost: config.Getenv("WAKAMITI_HOST", "localhost"),
		ServicePort: config.Getenv("WAKAMITI_POST", "7264"),
	}
	cli := client.Client{Config: conf}

	// Connect to WS and stream progress. Ctrl+C sends STOP.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	os.Exit(cli.Run(ctx, flag.Args()))
}
