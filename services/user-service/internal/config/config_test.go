package config

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestUserConfig_Load_Defaults(t *testing.T) {
	// Clear environment variables
	os.Unsetenv("PORT")
	os.Unsetenv("DATABASE_URL")
	os.Unsetenv("REDIS_URL")
	os.Unsetenv("REDIS_PASSWORD")
	os.Unsetenv("AUTH_SERVICE_URL")

	cfg := Load()

	assert.Equal(t, "8002", cfg.Server.Port)
	assert.Contains(t, cfg.Database.URL, "postgres")
	assert.Contains(t, cfg.Redis.URL, "redis")
	assert.NotEmpty(t, cfg.AuthService.URL)
}

func TestUserConfig_Load_EnvOverrides(t *testing.T) {
	os.Setenv("PORT", "9002")
	os.Setenv("DATABASE_URL", "postgres://custom:custom@localhost:5433/custom")
	os.Setenv("REDIS_URL", "redis://custom-redis:6380")
	os.Setenv("REDIS_PASSWORD", "custom-password")
	os.Setenv("AUTH_SERVICE_URL", "http://custom-auth:8001")

	defer func() {
		os.Unsetenv("PORT")
		os.Unsetenv("DATABASE_URL")
		os.Unsetenv("REDIS_URL")
		os.Unsetenv("REDIS_PASSWORD")
		os.Unsetenv("AUTH_SERVICE_URL")
	}()

	cfg := Load()

	assert.Equal(t, "9002", cfg.Server.Port)
	assert.Equal(t, "postgres://custom:custom@localhost:5433/custom", cfg.Database.URL)
	assert.Equal(t, "redis://custom-redis:6380", cfg.Redis.URL)
	assert.Equal(t, "custom-password", cfg.Redis.Password)
	assert.Equal(t, "http://custom-auth:8001", cfg.AuthService.URL)
}

func TestUserConfig_Load_YAMLFile(t *testing.T) {
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yaml")

	configContent := `
server:
  port: "7002"
database:
  url: "postgres://yaml:yaml@localhost:5433/yaml_db"
redis:
  url: "redis://yaml-redis:6379"
  password: "yaml-password"
authService:
  url: "http://yaml-auth:8001"
`
	err := os.WriteFile(configPath, []byte(configContent), 0644)
	require.NoError(t, err)

	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	cfg := Load()

	assert.Equal(t, "7002", cfg.Server.Port)
	assert.Equal(t, "postgres://yaml:yaml@localhost:5433/yaml_db", cfg.Database.URL)
	assert.Equal(t, "redis://yaml-redis:6379", cfg.Redis.URL)
	assert.Equal(t, "yaml-password", cfg.Redis.Password)
	assert.Equal(t, "http://yaml-auth:8001", cfg.AuthService.URL)
}

func TestUserConfig_Load_YAMLWithEnvOverride(t *testing.T) {
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yaml")

	configContent := `
server:
  port: "7002"
redis:
  url: "redis://yaml:6379"
`
	err := os.WriteFile(configPath, []byte(configContent), 0644)
	require.NoError(t, err)

	os.Setenv("PORT", "env-port")
	os.Setenv("REDIS_URL", "redis://env:6380")

	defer os.Unsetenv("PORT")
	defer os.Unsetenv("REDIS_URL")

	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	cfg := Load()

	// ENV should override YAML
	assert.Equal(t, "env-port", cfg.Server.Port)
	assert.Equal(t, "redis://env:6380", cfg.Redis.URL)
}

func TestUserConfig_FindConfigFile(t *testing.T) {
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yaml")

	err := os.WriteFile(configPath, []byte("test: config"), 0644)
	require.NoError(t, err)

	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	cfg := Load()
	assert.NotNil(t, cfg)
}

func TestUserConfig_FindConfigFileYML(t *testing.T) {
	tempDir := t.TempDir()
	configPath := filepath.Join(tempDir, "config.yml")

	err := os.WriteFile(configPath, []byte("test: config"), 0644)
	require.NoError(t, err)

	originalDir, _ := os.Getwd()
	os.Chdir(tempDir)
	defer os.Chdir(originalDir)

	cfg := Load()
	assert.NotNil(t, cfg)
}

func TestUserConfig_ApplyDefaults(t *testing.T) {
	cfg := &Config{}
	applyDefaults(cfg)

	assert.Equal(t, "8002", cfg.Server.Port)
	assert.Contains(t, cfg.Database.URL, "5433") // user-service uses 5433
	assert.Contains(t, cfg.Redis.URL, "redis")
	assert.Equal(t, "http://localhost:8001", cfg.AuthService.URL)
}

func TestUserConfig_PartialDefaults(t *testing.T) {
	cfg := &Config{}
	cfg.Server.Port = "9999"
	// Leave other fields empty

	applyDefaults(cfg)

	assert.Equal(t, "9999", cfg.Server.Port) // Should not override existing
	assert.Contains(t, cfg.Database.URL, "postgres")
}
