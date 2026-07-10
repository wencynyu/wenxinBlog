package config

import (
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Server struct {
		Port string `yaml:"port"`
	} `yaml:"server"`

	Database struct {
		URL string `yaml:"url"`
	} `yaml:"database"`

	Redis struct {
		URL string `yaml:"url"`
	} `yaml:"redis"`

	JWT struct {
		Secret string `yaml:"secret"`
		Expiry int    `yaml:"expiry"`
	} `yaml:"jwt"`

	OAuth struct {
		Google OAuthConfig `yaml:"google"`
		GitHub OAuthConfig `yaml:"github"`
		WeChat OAuthConfig `yaml:"wechat"`
	} `yaml:"oauth"`
}

type OAuthConfig struct {
	ClientID     string `yaml:"clientId"`
	ClientSecret string `yaml:"clientSecret"`
	RedirectURL  string `yaml:"redirectUrl"`
}

// Load reads config from config.yaml in the project root.
// Falls back to environment variables for backward compatibility.
func Load() *Config {
	cfg := &Config{}

	// Try loading from YAML file first
	configPath := findConfigFile()
	if configPath != "" {
		data, err := os.ReadFile(configPath)
		if err == nil {
			if err := yaml.Unmarshal(data, cfg); err != nil {
				fmt.Printf("Warning: failed to parse %s: %v\n", configPath, err)
			} else {
				fmt.Printf("Loaded config from %s\n", configPath)
			}
		}
	}

	// Apply defaults for empty values
	applyDefaults(cfg)

	// Environment variables override (backward compatible)
	applyEnvOverrides(cfg)

	return cfg
}

func findConfigFile() string {
	// Search in current directory and parent directories
	candidates := []string{
		"config.yaml",
		"config.yml",
	}

	// Start from executable directory
	dir, err := os.Getwd()
	if err != nil {
		return ""
	}

	for {
		for _, name := range candidates {
			path := filepath.Join(dir, name)
			if _, err := os.Stat(path); err == nil {
				return path
			}
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			break
		}
		dir = parent
	}

	return ""
}

func applyDefaults(cfg *Config) {
	if cfg.Server.Port == "" {
		cfg.Server.Port = "8001"
	}
	if cfg.Database.URL == "" {
		cfg.Database.URL = "postgres://postgres:postgres@localhost:5432/auth_db?sslmode=disable"
	}
	if cfg.Redis.URL == "" {
		cfg.Redis.URL = "redis://localhost:6379"
	}
	// NOTE: JWT secret has intentionally NO default. It MUST be provided
	// via the JWT_SECRET environment variable (see .env.example).
	// main.go fails fast if it is still empty after loading.
	if cfg.JWT.Expiry == 0 {
		cfg.JWT.Expiry = 86400
	}
}

func applyEnvOverrides(cfg *Config) {
	if v := os.Getenv("PORT"); v != "" {
		cfg.Server.Port = v
	}
	if v := os.Getenv("DATABASE_URL"); v != "" {
		cfg.Database.URL = v
	}
	if v := os.Getenv("REDIS_URL"); v != "" {
		cfg.Redis.URL = v
	}
	if v := os.Getenv("JWT_SECRET"); v != "" {
		cfg.JWT.Secret = v
	}
	if v := os.Getenv("JWT_EXPIRY"); v != "" {
		var val int
		if _, err := fmt.Sscanf(v, "%d", &val); err == nil {
			cfg.JWT.Expiry = val
		}
	}

	if v := os.Getenv("GOOGLE_CLIENT_ID"); v != "" {
		cfg.OAuth.Google.ClientID = v
	}
	if v := os.Getenv("GOOGLE_CLIENT_SECRET"); v != "" {
		cfg.OAuth.Google.ClientSecret = v
	}
	if v := os.Getenv("GOOGLE_REDIRECT_URL"); v != "" {
		cfg.OAuth.Google.RedirectURL = v
	}

	if v := os.Getenv("GITHUB_CLIENT_ID"); v != "" {
		cfg.OAuth.GitHub.ClientID = v
	}
	if v := os.Getenv("GITHUB_CLIENT_SECRET"); v != "" {
		cfg.OAuth.GitHub.ClientSecret = v
	}
	if v := os.Getenv("GITHUB_REDIRECT_URL"); v != "" {
		cfg.OAuth.GitHub.RedirectURL = v
	}

	if v := os.Getenv("WECHAT_APP_ID"); v != "" {
		cfg.OAuth.WeChat.ClientID = v
	}
	if v := os.Getenv("WECHAT_APP_SECRET"); v != "" {
		cfg.OAuth.WeChat.ClientSecret = v
	}
	if v := os.Getenv("WECHAT_REDIRECT_URL"); v != "" {
		cfg.OAuth.WeChat.RedirectURL = v
	}
}
