package client

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestNewConfig(t *testing.T) {
	// Setup temporary directory for test files
	tmpDir := Getenv("BUILD_DIR", t.TempDir())

	// Create effective.properties
	effectivePropsContent := `
server.host=192.168.1.100
server.port=8080
server.auth.origin=test-origin
`
	effectivePropsPath := filepath.Join(tmpDir, "effective.properties")
	if err := os.WriteFile(effectivePropsPath, []byte(effectivePropsContent), 0644); err != nil {
		t.Fatalf("Failed to create effective.properties: %v", err)
	}

	// Create wakamiti.properties
	wakamitiPropsContent := "effective.properties=" + effectivePropsPath + "\n"
	// We need to write this to the current working directory because NewConfig looks for "wakamiti.properties"
	// So we'll change the working directory for the test
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

	// Test NewConfig
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

	// Create wakamiti.properties pointing to non-existent file
	wakamitiPropsContent := "effective.properties=non-existent.properties\n"
	os.WriteFile("wakamiti.properties", []byte(wakamitiPropsContent), 0644)

	_, err := NewConfig()
	if err == nil {
		t.Error("NewConfig should fail when effective.properties file is missing")
	}
}

// Getenv retrieves an environment variable or returns a default value if not set.
func Getenv(key, defaultValue string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return defaultValue
	}
	return value
}
