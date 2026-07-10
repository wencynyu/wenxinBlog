package middleware

import (
	"strings"

	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
)

// TokenValidator is a minimal interface for token validation (used by middleware)
type TokenValidator interface {
	ValidateToken(token string) (*service.Claims, error)
}

func AuthMiddleware(authService TokenValidator) fiber.Handler {
	return func(c *fiber.Ctx) error {
		authHeader := c.Get("Authorization")
		if authHeader == "" {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "missing authorization header"})
		}
		token := strings.TrimPrefix(authHeader, "Bearer ")
		if token == authHeader {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "invalid authorization format"})
		}
		claims, err := authService.ValidateToken(token)
		if err != nil {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "invalid or expired token"})
		}
		c.Locals("userId", claims.UserID)
		c.Locals("roles", claims.Roles)
		return c.Next()
	}
}
