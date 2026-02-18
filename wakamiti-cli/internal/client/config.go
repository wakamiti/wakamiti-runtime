/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package client

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// Config contains the minimum runtime settings required by the CLI client.
//
// Values are loaded from Java-style .properties files (wakamiti.properties
// and optionally an "effective.properties" file).
type Config struct {
	ServiceHost string
	ServicePort string
	Origin      string
}

// NewConfig discovers and loads runtime configuration from disk.
//
// Resolution order:
// 1. Look for "wakamiti.properties" in current working directory.
// 2. If not found, look for it next to the executable.
// 3. If "effective.properties" is declared, merge it on top.
//
// Mandatory keys after merge:
// - server.host
// - server.port
// - server.auth.origin
func NewConfig() (*Config, error) {
	// 1. Try to find the installation directory
	exePath, err := os.Executable()
	if err != nil {
		return nil, fmt.Errorf("could not get executable path: %w", err)
	}
	exeDir := filepath.Dir(exePath)

	// Locations to look for wakamiti.properties
	searchPaths := []string{
		"wakamiti.properties", // Current directory
		filepath.Join(exeDir, "wakamiti.properties"),
	}

	var props map[string]string
	var propsPath string
	for _, path := range searchPaths {
		if p, err := LoadProperties(path); err == nil {
			props = p
			propsPath = path
			break
		}
	}

	if props == nil {
		return nil, fmt.Errorf("could not find wakamiti.properties in any of: %v", searchPaths)
	}

	// 2. Load effective properties
	effectivePropertiesFile := ""
	if val, ok := props["effective.properties"]; ok {
		effectivePropertiesFile = val
	}

	if effectivePropertiesFile != "" {
		// If effectivePropertiesFile is relative, make it relative to wakamiti.properties
		if !filepath.IsAbs(effectivePropertiesFile) {
			effectivePropertiesFile = filepath.Join(filepath.Dir(propsPath), effectivePropertiesFile)
		}

		// Read effective properties file
		effectiveProps, err := LoadProperties(effectivePropertiesFile)
		if err == nil {
			// Merge effective properties into props
			for k, v := range effectiveProps {
				props[k] = v
			}
		} else {
			return nil, fmt.Errorf("could not read effective properties file at %s: %w", effectivePropertiesFile, err)
		}
	}

	return &Config{
		ServiceHost: props["server.host"],
		ServicePort: props["server.port"],
		Origin:      props["server.auth.origin"],
	}, validateConfig(props["server.host"], props["server.port"], props["server.auth.origin"])
}

func unescapePropertiesValue(s string) string {
	var b strings.Builder
	b.Grow(len(s))

	for i := 0; i < len(s); i++ {
		c := s[i]
		if c != '\\' || i+1 >= len(s) {
			b.WriteByte(c)
			continue
		}

		n := s[i+1]

		switch n {
		case ':', '=', ' ', '\\', '#', '!':
			b.WriteByte(n)
			i++
		default:
			b.WriteByte('\\')
		}
	}

	return b.String()
}

// LoadProperties parses a Java-style .properties file into key/value pairs.
//
// Supported features:
// - comments starting with '#',
// - escaping for ':', '=', space, '\', '#', '!',
// - variable replacement for ${user.home},
// - simple iterative replacement for ${other.property}.
//
// This function intentionally keeps parsing rules small and predictable.
func LoadProperties(filename string) (map[string]string, error) {
	props := make(map[string]string)
	file, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			key := strings.TrimSpace(parts[0])
			value := strings.TrimSpace(parts[1])
			value = unescapePropertiesValue(value)
			props[key] = value
		}
	}

	if err := scanner.Err(); err != nil {
		return nil, err
	}

	// Resolve ${user.home}
	userHome, err := os.UserHomeDir()
	if err == nil {
		for k, v := range props {
			props[k] = strings.ReplaceAll(v, "${user.home}", userHome)
		}
	}

	// Resolve other properties
	// Simple iterative resolution to handle dependencies
	for i := 0; i < 5; i++ { // Limit iterations to avoid infinite loops
		changed := false
		for k, v := range props {
			for pk, pv := range props {
				if strings.Contains(v, "${"+pk+"}") {
					newValue := strings.ReplaceAll(v, "${"+pk+"}", pv)
					if newValue != v {
						props[k] = newValue
						v = newValue
						changed = true
					}
				}
			}
		}
		if !changed {
			break
		}
	}

	return props, nil
}

func validateConfig(serviceHost, servicePort, origin string) error {
	if strings.TrimSpace(serviceHost) == "" {
		return errorsConfig("server.host is required")
	}
	if strings.TrimSpace(servicePort) == "" {
		return errorsConfig("server.port is required")
	}
	port, err := strconv.Atoi(strings.TrimSpace(servicePort))
	if err != nil || port < 1 || port > 65535 {
		return errorsConfig("server.port must be a valid TCP port (1-65535)")
	}
	if strings.TrimSpace(origin) == "" {
		return errorsConfig("server.auth.origin is required")
	}
	return nil
}

func errorsConfig(message string) error {
	return fmt.Errorf("invalid configuration: %s", message)
}
