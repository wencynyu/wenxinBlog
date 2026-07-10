package config

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoad_Defaults(t *testing.T) {
	// Clear environment variables that might interfere
	os.Unsetenv("PORT")
	os.Unsetenv("DATABASE_URL")
	os.Unsetenv("REDIS_URL")
	os.Unsetenv("JWT_SECRET")

	cfg := Load()

	assert.Equal(t, "8001", cfg.Server.Port)
	assert.Contains(t, cfg.Database.URL, "postgres")
	assert.Contains(t, cfg.Redis.URL, "redis")
	// JWT secret has no default; it must come from JWT_SECRET env.
	assert.Empty(t, cfg.JWT.Secret)
	assert.Equal(t, 86400, cfg.JWT.Expiry)
}

func TestLoad_EnvOverrides(t *testing.T) {
	// Set environment variables
	os.Setenv("PORT", "9001")
	os.Setenv("DATABASE_URL", "postgres://custom:custom@localhost:5432/custom")
	os.Setenv("REDIS_URL", "redis://custom-redis:6380")
	os.Setenv("JWT_SECRET", "custom-secret")
	os.Setenv("JWT_EXPIRY", "3600")

	defer func() {
		os.Unsetenv("PORT")
		os.Unsetenv("DATABASE_URL")
		os.Unsetenv("REDIS_URL")
		os.Unsetenv("JWT_SECRET")
		os.Unsetenv("JWT_EXPIRY")
	}()

	cfg := Load()

	assert.Equal(t, "9001", cfg.Server.Port)
	assert.Equal(t, "postgres://custom:custom@localhost:5432/custom", cfg.Database.URL)
	assert.Equal(t, "redis://custom-redis:6380", cfg.Redis.URL)
	assert.Equal(t, "custom-secret", cfg.JWT.Secret)
	assert.Equal(t, 3600, cfg.JWT.Expiry)
}

func TestLoad_OAuthEnvOverrides(t *testing.T) {
	os.Setenv("GOOGLE_CLIENT_ID", "google-client-id")
	os.Setenv("GOOGLE_CLIENT_SECRET", "google-secret")
	os.Setenv("GOOGLE_REDIRECT_URL", "https://example.com/google/callback")

	os.Setenv("GITHUB_CLIENT_ID", "github-client-id")
	os.Setenv("GITHUB_CLIENT_SECRET", "github-secret")
	os.Setenv("GITHUB_REDIRECT_URL", "https://example.com/github/callback")

	os.Setenv("WECHAT_APP_ID", "wechat-app-id")
	os.Setenv("WECHAT_APP_SECRET", "wechat-secret")
	os.Setenv("WECHAT_REDIRECT_URL", "https://example.com/wechat/callback")

	defer func() {
		os.Unsetenv("GOOGLE_CLIENT_ID")
		os.Unsetenv("GOOGLE_CLIENT_SECRET")
		os.Unsetenv("GOOGLE_REDIRECT_URL")
		os.Unsetenv("GITHUB_CLIENT_ID")
		os.Unsetenv("GITHUB_CLIENT_SECRET")
		os.Unsetenv("GITHUB_REDIRECT_URL")
		os.Unsetenv("WECHAT_APP_ID")
		os.Unsetenv("WECHAT_APP_SECRET")
		os.Unsetenv("WECHAT_REDIRECT_URL")
	}()

	cfg := Load()

	assert.Equal(t, "google-client-id", cfg.OAuth.Google.ClientID)
	assert.Equal(t, "google-secret", cfg.OAuth.Google.ClientSecret)
	assert.Equal(t, "https://example.com/google/callback", cfg.OAuth.Google.RedirectURL)

	assert.Equal(t, "github-client-id", cfg.OAuth.GitHub.ClientID)
	assert.Equal(t, "github-secret", cfg.OAuth.GitHub.ClientSecret)
	assert.Equal(t, "https://example.com/github/callback", cfg.OAuth.GitHub.RedirectURL)

	assert.Equal(t, "wechat-app-id", cfg.OAuth.WeChat.ClientID)
	assert.Equal(t, "wechat-secret", cfg.OAuth.WeChat.ClientSecret)
	assert.Equal(t, "https://example.com/wechat/callback", cfg.OAuth.WeChat.RedirectURL)
}

func TestLoad_YAMLFile(t *testing.T) {
	// Create a temporary config file
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yaml")

	configContent := `
server:
  port: "7001"
database:
  url: "postgres://yaml:yaml@localhost:5432/yaml_db"
redis:
  url: "redis://yaml-redis:6379"
jwt:
  secret: "yaml-secret-key"
  expiry: 7200
`
	err := os.WriteFile(configPath, []byte(configContent), 0644)
	require.NoError(t, err)

	// Change to temp directory to find the config
	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	cfg := Load()

	assert.Equal(t, "7001", cfg.Server.Port)
	assert.Equal(t, "postgres://yaml:yaml@localhost:5432/yaml_db", cfg.Database.URL)
	assert.Equal(t, "redis://yaml-redis:6379", cfg.Redis.URL)
	assert.Equal(t, "yaml-secret-key", cfg.JWT.Secret)
	assert.Equal(t, 7200, cfg.JWT.Expiry)
}

func TestLoad_YAMLWithEnvOverride(t *testing.T) {
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yaml")

	configContent := `
server:
  port: "7001"
jwt:
  secret: "yaml-secret"
`
	err := os.WriteFile(configPath, []byte(configContent), 0644)
	require.NoError(t, err)

	os.Setenv("PORT", "env-port")
	os.Setenv("JWT_SECRET", "env-secret")

	defer os.Unsetenv("PORT")
	defer os.Unsetenv("JWT_SECRET")

	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	cfg := Load()

	// ENV should override YAML
	assert.Equal(t, "env-port", cfg.Server.Port)
	assert.Equal(t, "env-secret", cfg.JWT.Secret)
}

func TestFindConfigFile(t *testing.T) {
	// Test with config file in current directory
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yaml")

	err := os.WriteFile(configPath, []byte("test: config"), 0644)
	require.NoError(t, err)

	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	// This should find the config file
	cfg := Load()
	assert.NotNil(t, cfg)
}

func TestApplyDefaults(t *testing.T) {
	cfg := &Config{}
	applyDefaults(cfg)

	assert.Equal(t, "8001", cfg.Server.Port)
	assert.Contains(t, cfg.Database.URL, "postgres")
	assert.Contains(t, cfg.Redis.URL, "redis")
	// JWT secret has no default after the security hardening.
	assert.Empty(t, cfg.JWT.Secret)
	assert.Equal(t, 86400, cfg.JWT.Expiry)
}

func TestPartialDefaults(t *testing.T) {
	cfg := &Config{}
	cfg.Server.Port = "9999"
	// Leave other fields empty

	applyDefaults(cfg)

	assert.Equal(t, "9999", cfg.Server.Port) // Should not override existing
	assert.Contains(t, cfg.Database.URL, "postgres") // Should set default
}
