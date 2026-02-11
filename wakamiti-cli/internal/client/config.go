/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package client

import (
	"os"
	"strings"
)

// Config holds the configuration for the Wakamiti CLI.
type Config struct {
	ServiceHost string
	ServicePort string
}

// Getenv retrieves an environment variable or returns a default value if not set.
func Getenv(key, defaultValue string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return defaultValue
	}
	return value
}
