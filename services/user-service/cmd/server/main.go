package main

import (
	"context"
	"database/sql"
	"log"

	"wenxinblog/user-service/internal/config"
	"wenxinblog/user-service/internal/handler"
	"wenxinblog/user-service/internal/middleware"
	"wenxinblog/user-service/internal/observability"
	"wenxinblog/user-service/internal/repository"
	"wenxinblog/user-service/internal/service"

	"github.com/gofiber/contrib/otelfiber/v2"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	_ "github.com/lib/pq"
	"github.com/redis/go-redis/v9"
)

func main() {
	cfg := config.Load()

	// OTel：traces + metrics 经 OTLP gRPC → collector
	shutdown := observability.Setup("user-service")
	defer shutdown(context.Background())

	// Connect PostgreSQL
	db, err := sql.Open("postgres", cfg.Database.URL)
	if err != nil {
		log.Fatalf("Failed to connect database: %v", err)
	}
	defer db.Close()
	if err := db.Ping(); err != nil {
		log.Fatalf("Database ping failed: %v", err)
	}

	// Connect Redis
	rdb := redis.NewClient(&redis.Options{
		Addr:     cfg.Redis.URL,
		Password: cfg.Redis.Password,
	})
	defer rdb.Close()

	// Initialize repositories
	profileRepo := repository.NewProfileRepository(db)
	followRepo := repository.NewFollowRepository(db)
	statsRepo := repository.NewStatsRepository(db, rdb)

	// Initialize service
	userSvc := service.NewUserService(profileRepo, followRepo, statsRepo)

	// Create Fiber app
	app := fiber.New(fiber.Config{
		AppName:      "WenxinBlog User Service",
		ServerHeader: "WenxinBlog",
	})

	// Middleware
	app.Use(logger.New())
	app.Use(recover.New())
	app.Use(cors.New())

	// OTel HTTP 中间件：自动给每条请求创建 span + HTTP metrics（替代 fiberprometheus）。
	// service.name 来自 otel.go 里配置的 TracerProvider resource。
	app.Use(otelfiber.Middleware())

	// Health check
	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "healthy", "service": "user-service"})
	})

	// API routes
	api := app.Group("/api/v1")
	h := handler.NewUserHandler(userSvc)

	users := api.Group("/users")
	users.Get("/:id", h.GetProfile)
	users.Get("/:id/stats", h.GetStats)
	users.Get("/:id/followers", h.GetFollowers)
	users.Get("/:id/following", h.GetFollowing)
	users.Get("/search", h.SearchUsers)

	users.Put("/:id", middleware.AuthMiddleware(), h.UpdateProfile)
	users.Post("/:id/follow", middleware.AuthMiddleware(), h.FollowUser)
	users.Delete("/:id/follow", middleware.AuthMiddleware(), h.UnfollowUser)

	api.Get("/me/following", middleware.AuthMiddleware(), h.GetMyFollowing)

	log.Printf("User Service starting on port %s", cfg.Server.Port)
	log.Fatal(app.Listen(":" + cfg.Server.Port))
}
