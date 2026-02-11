package client

import (
	"os"
	"testing"
)

func TestGetenv(t *testing.T) {
	key := "WAKAMITI_TEST_VAR"
	def := "default"

	// Case 1: Not set
	os.Unsetenv(key)
	if v := Getenv(key, def); v != def {
		t.Fatalf("v=%q want %q", v, def)
	}

	// Case 2: Set
	val := "custom"
	os.Setenv(key, val)
	if v := Getenv(key, def); v != val {
		t.Fatalf("v=%q want %q", v, val)
	}

	// Case 3: Empty string
	os.Setenv(key, "  ")
	if v := Getenv(key, def); v != def {
		t.Fatalf("v=%q want %q", v, def)
	}
}
