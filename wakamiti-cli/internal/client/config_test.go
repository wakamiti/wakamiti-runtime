/*
 * Copyright (c) 2022-2026 Instituto Tecnológico de Informática (ITI)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package client

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestNewConfig(t *testing.T) {
	// Set up temporary directory for test files.
	tmpDir := Getenv("BUILD_DIR", t.TempDir())

	// Create effective.properties with final values used by the client.
	effectivePropsContent := `
server.host=192.168.1.100
server.port=8080
server.auth.origin=test-origin
`
	effectivePropsPath := filepath.Join(tmpDir, "effective.properties")
	if err := os.WriteFile(effectivePropsPath, []byte(effectivePropsContent), 0644); err != nil {
		t.Fatalf("Failed to create effective.properties: %v", err)
	}

	// Create wakamiti.properties.
	wakamitiPropsContent := "effective.properties=" + effectivePropsPath + "\n"
	// NewConfig checks current working directory first, so move test process there.
	originalWd, err := os.Getwd()
	if err != nil {
		t.Fatalf("Failed to get current working directory: %v", err)
	}
	defer os.Chdir(originalWd)

	if err := os.Chdir(tmpDir); err != nil {
		t.Fatalf("Failed to change working directory: %v", err)
	}

	if err := os.WriteFile("wakamiti.properties", []byte(wakamitiPropsContent), 0644); err != nil {
		t.Fatalf("Failed to create wakamiti.properties: %v", err)
	}

	// Validate merged config.
	config, err := NewConfig()
	if err != nil {
		t.Fatalf("NewConfig failed: %v", err)
	}

	if config.ServiceHost != "192.168.1.100" {
		t.Errorf("ServiceHost=%q want %q", config.ServiceHost, "192.168.1.100")
	}
	if config.ServicePort != "8080" {
		t.Errorf("ServicePort=%q want %q", config.ServicePort, "8080")
	}
	if config.Origin != "test-origin" {
		t.Errorf("Origin=%q want %q", config.Origin, "test-origin")
	}
}

func TestNewConfig_MissingWakamitiProperties(t *testing.T) {
	tmpDir := t.TempDir()
	originalWd, _ := os.Getwd()
	defer os.Chdir(originalWd)
	os.Chdir(tmpDir)

	_, err := NewConfig()
	if err == nil {
		t.Error("NewConfig should fail when wakamiti.properties is missing")
	}
}

func TestNewConfig_MissingEffectiveProperties(t *testing.T) {
	tmpDir := t.TempDir()
	originalWd, _ := os.Getwd()
	defer os.Chdir(originalWd)
	os.Chdir(tmpDir)

	// Create wakamiti.properties pointing to non-existent file.
	wakamitiPropsContent := "effective.properties=non-existent.properties\n"
	os.WriteFile("wakamiti.properties", []byte(wakamitiPropsContent), 0644)

	_, err := NewConfig()
	if err == nil {
		t.Error("NewConfig should fail when effective.properties file is missing")
	}
}

func TestNewConfig_RelativeEffectiveProperties(t *testing.T) {
	tmpDir := t.TempDir()
	originalWd, err := os.Getwd()
	if err != nil {
		t.Fatalf("Failed to get current working directory: %v", err)
	}
	defer os.Chdir(originalWd)

	if err := os.Chdir(tmpDir); err != nil {
		t.Fatalf("Failed to change working directory: %v", err)
	}

	if err := os.Mkdir("config", 0755); err != nil {
		t.Fatalf("Failed to create config directory: %v", err)
	}

	if err := os.WriteFile("wakamiti.properties", []byte("effective.properties=config/effective.properties\n"), 0644); err != nil {
		t.Fatalf("Failed to create wakamiti.properties: %v", err)
	}
	if err := os.WriteFile(filepath.Join("config", "effective.properties"), []byte(strings.Join([]string{
		"server.host=127.0.0.1",
		"server.port=7264",
		"server.auth.origin=waka.cli",
	}, "\n")), 0644); err != nil {
		t.Fatalf("Failed to create relative effective.properties: %v", err)
	}

	config, err := NewConfig()
	if err != nil {
		t.Fatalf("NewConfig failed: %v", err)
	}

	if config.ServiceHost != "127.0.0.1" {
		t.Errorf("ServiceHost=%q want %q", config.ServiceHost, "127.0.0.1")
	}
	if config.ServicePort != "7264" {
		t.Errorf("ServicePort=%q want %q", config.ServicePort, "7264")
	}
	if config.Origin != "waka.cli" {
		t.Errorf("Origin=%q want %q", config.Origin, "waka.cli")
	}
}

func TestLoadProperties_ResolvesUserHomeAndNestedReferences(t *testing.T) {
	tmpDir := t.TempDir()
	propsPath := filepath.Join(tmpDir, "wakamiti.properties")

	content := strings.Join([]string{
		"base.path=${user.home}/.wakamiti",
		"server.system.path=${base.path}/system",
		"server.log.path=${server.system.path}/log",
		"server.host=127.0.0.1",
		"server.port=7264",
		"server.auth.origin=waka.cli",
	}, "\n")
	if err := os.WriteFile(propsPath, []byte(content), 0644); err != nil {
		t.Fatalf("Failed to create properties file: %v", err)
	}

	props, err := LoadProperties(propsPath)
	if err != nil {
		t.Fatalf("LoadProperties failed: %v", err)
	}

	home, err := os.UserHomeDir()
	if err != nil {
		t.Fatalf("Failed to resolve user home: %v", err)
	}
	wantBasePath := home + "/.wakamiti"
	wantSystemPath := wantBasePath + "/system"
	wantLogPath := wantSystemPath + "/log"

	if props["base.path"] != wantBasePath {
		t.Errorf("base.path=%q want %q", props["base.path"], wantBasePath)
	}
	if props["server.system.path"] != wantSystemPath {
		t.Errorf("server.system.path=%q want %q", props["server.system.path"], wantSystemPath)
	}
	if props["server.log.path"] != wantLogPath {
		t.Errorf("server.log.path=%q want %q", props["server.log.path"], wantLogPath)
	}
}

func TestLoadProperties_UnescapesSpecialCharacters(t *testing.T) {
	tmpDir := t.TempDir()
	propsPath := filepath.Join(tmpDir, "escaped.properties")

	content := strings.Join([]string{
		`message=hello\ world`,
		`symbolic=a\=b`,
		`path=c\:\\temp\\wakamiti`,
		`comment=value\#1`,
	}, "\n")
	if err := os.WriteFile(propsPath, []byte(content), 0644); err != nil {
		t.Fatalf("Failed to create escaped properties file: %v", err)
	}

	props, err := LoadProperties(propsPath)
	if err != nil {
		t.Fatalf("LoadProperties failed: %v", err)
	}

	if props["message"] != "hello world" {
		t.Errorf("message=%q want %q", props["message"], "hello world")
	}
	if props["symbolic"] != "a=b" {
		t.Errorf("symbolic=%q want %q", props["symbolic"], "a=b")
	}
	if props["path"] != `c:\temp\wakamiti` {
		t.Errorf("path=%q want %q", props["path"], `c:\temp\wakamiti`)
	}
	if props["comment"] != "value#1" {
		t.Errorf("comment=%q want %q", props["comment"], "value#1")
	}
}

func TestNewConfig_InvalidPort(t *testing.T) {
	tmpDir := t.TempDir()
	originalWd, err := os.Getwd()
	if err != nil {
		t.Fatalf("Failed to get current working directory: %v", err)
	}
	defer os.Chdir(originalWd)

	if err := os.Chdir(tmpDir); err != nil {
		t.Fatalf("Failed to change working directory: %v", err)
	}

	content := strings.Join([]string{
		"server.host=127.0.0.1",
		"server.port=70000",
		"server.auth.origin=waka.cli",
	}, "\n")
	if err := os.WriteFile("wakamiti.properties", []byte(content), 0644); err != nil {
		t.Fatalf("Failed to create wakamiti.properties: %v", err)
	}

	_, err = NewConfig()
	if err == nil {
		t.Fatal("NewConfig should fail when server.port is invalid")
	}
	if !strings.Contains(err.Error(), "server.port must be a valid TCP port") {
		t.Fatalf("err=%q want invalid port validation message", err.Error())
	}
}

func TestNewConfig_MissingOrigin(t *testing.T) {
	tmpDir := t.TempDir()
	originalWd, err := os.Getwd()
	if err != nil {
		t.Fatalf("Failed to get current working directory: %v", err)
	}
	defer os.Chdir(originalWd)

	if err := os.Chdir(tmpDir); err != nil {
		t.Fatalf("Failed to change working directory: %v", err)
	}

	content := strings.Join([]string{
		"server.host=127.0.0.1",
		"server.port=7264",
	}, "\n")
	if err := os.WriteFile("wakamiti.properties", []byte(content), 0644); err != nil {
		t.Fatalf("Failed to create wakamiti.properties: %v", err)
	}

	_, err = NewConfig()
	if err == nil {
		t.Fatal("NewConfig should fail when server.auth.origin is missing")
	}
	if !strings.Contains(err.Error(), "server.auth.origin is required") {
		t.Fatalf("err=%q want missing origin validation message", err.Error())
	}
}

// Getenv retrieves an environment variable or returns a fallback value.
func Getenv(key, defaultValue string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return defaultValue
	}
	return value
}
