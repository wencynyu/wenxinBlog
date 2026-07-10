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
		URL      string `yaml:"url"`
		Password string `yaml:"password"`
	} `yaml:"redis"`

	AuthService struct {
		URL string `yaml:"url"`
	} `yaml:"authService"`
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
	candidates := []string{
		"config.yaml",
		"config.yml",
	}

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
		cfg.Server.Port = "8002"
	}
	if cfg.Database.URL == "" {
		cfg.Database.URL = "postgres://postgres:postgres@localhost:5433/user_db?sslmode=disable"
	}
	if cfg.Redis.URL == "" {
		cfg.Redis.URL = "redis://localhost:6379"
	}
	if cfg.AuthService.URL == "" {
		cfg.AuthService.URL = "http://localhost:8001"
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
	if v := os.Getenv("REDIS_PASSWORD"); v != "" {
		cfg.Redis.Password = v
	}
	if v := os.Getenv("AUTH_SERVICE_URL"); v != "" {
		cfg.AuthService.URL = v
	}
}
