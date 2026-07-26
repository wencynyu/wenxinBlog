package main

import (
	"database/sql"
	"log"

	"wenxinblog/auth-service/internal/config"
	"wenxinblog/auth-service/internal/handler"
	"wenxinblog/auth-service/internal/repository"
	"wenxinblog/auth-service/internal/service"

	"github.com/ansrivas/fiberprometheus/v2"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	_ "github.com/lib/pq"
)

func main() {
	cfg := config.Load()

	// Fail fast: JWT secret must be provided via JWT_SECRET env.
	if cfg.JWT.Secret == "" {
		log.Fatal("JWT_SECRET environment variable is required (see .env.example)")
	}

	// Connect PostgreSQL
	db, err := sql.Open("postgres", cfg.Database.URL)
	if err != nil {
		log.Fatalf("Failed to connect database: %v", err)
	}
	defer db.Close()
	if err := db.Ping(); err != nil {
		log.Fatalf("Database ping failed: %v", err)
	}

	// Initialize dependencies
	userRepo := repository.NewUserRepo(db)
	jwtService := service.NewJWTService(cfg.JWT.Secret)
	authService := service.NewAuthService(userRepo, jwtService)

	// Create Fiber app
	app := fiber.New(fiber.Config{
		AppName:      "WenxinBlog Auth Service",
		ServerHeader: "WenxinBlog",
	})

	// Middleware
	app.Use(logger.New())
	app.Use(recover.New())
	app.Use(cors.New())

	// Prometheus metrics（每路由自动采集 QPS + 延迟直方图）
	promMetrics := fiberprometheus.New("auth-service")
	promMetrics.RegisterAt(app, "/metrics")
	app.Use(promMetrics.Middleware)

	// Health check
	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"status":  "healthy",
			"service": "auth-service",
		})
	})

	// API routes
	api := app.Group("/api/v1")
	handler.RegisterRoutes(api, authService)

	log.Printf("Auth Service starting on port %s", cfg.Server.Port)
	log.Fatal(app.Listen(":" + cfg.Server.Port))
}
