package middleware

import (
	"github.com/gofiber/fiber/v2"
)

func AuthMiddleware() fiber.Handler {
	return func(c *fiber.Ctx) error {
		userID := c.Get("X-User-Id")
		if userID == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"code":    -1,
				"message": "X-User-Id header is required",
			})
		}
		c.Locals("userID", userID)
		c.Locals("permissions", c.Get("X-User-Permissions"))
		return c.Next()
	}
}
