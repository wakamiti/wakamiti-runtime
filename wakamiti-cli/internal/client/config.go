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
	"strings"
)

// Config holds the configuration for the Wakamiti CLI.
type Config struct {
	ServiceHost string
	ServicePort string
	Origin      string
}

// NewConfig creates a new Config instance by loading properties from files and environment variables.
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
	}, nil
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

// LoadProperties reads a properties file and returns a map of key-value pairs.
// It supports variable substitution for ${user.home}.
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
			value = filepath.Clean(value)
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
