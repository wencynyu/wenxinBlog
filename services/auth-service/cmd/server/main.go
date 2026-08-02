package main

import (
	"context"
	"database/sql"
	"log"

	"wenxinblog/auth-service/internal/config"
	"wenxinblog/auth-service/internal/handler"
	"wenxinblog/auth-service/internal/migrate"
	"wenxinblog/auth-service/internal/observability"
	"wenxinblog/auth-service/internal/repository"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/contrib/otelfiber/v2"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	_ "github.com/lib/pq"
)

func main() {
	cfg := config.Load()

	// OTel：traces + metrics 经 OTLP gRPC → collector
	shutdown := observability.Setup("auth-service")
	defer shutdown(context.Background())

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

	// 应用 db/migrations 迁移（含 RBAC 表 + 种子）。失败即退出：缺表时后续角色解析无法工作。
	if err := migrate.Run(db, "db/migrations"); err != nil {
		log.Fatalf("Database migration failed: %v", err)
	}

	// admin 引导：若配置了 ADMIN_BOOTSTRAP_EMAIL，为对应用户授予 admin 角色（幂等，可每次启动执行）。
	if cfg.Admin.BootstrapEmail != "" {
		if err := bootstrapAdmin(db, cfg.Admin.BootstrapEmail); err != nil {
			log.Printf("admin bootstrap failed: %v", err)
		} else {
			log.Printf("admin role ensured for %s", cfg.Admin.BootstrapEmail)
		}
	}

	// Initialize dependencies
	userRepo := repository.NewUserRepo(db)
	roleRepo := repository.NewRoleRepo(db)
	jwtService := service.NewJWTService(cfg.JWT.Secret)
	authService := service.NewAuthService(userRepo, roleRepo, jwtService, service.NewHTTPUserSyncClient(cfg.UserService.URL))

	// Create Fiber app
	app := fiber.New(fiber.Config{
		AppName:      "WenxinBlog Auth Service",
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

// bootstrapAdmin 为指定 email 的用户授予 admin 角色（幂等）。用于首次部署/开发时引导管理员。
func bootstrapAdmin(db *sql.DB, email string) error {
	_, err := db.Exec(`
		INSERT INTO user_roles (user_id, role_id)
		SELECT u.id, r.id FROM users u, roles r
		WHERE u.email = $1 AND r.code = 'admin'
		ON CONFLICT (user_id, role_id) DO NOTHING`, email)
	return err
}
