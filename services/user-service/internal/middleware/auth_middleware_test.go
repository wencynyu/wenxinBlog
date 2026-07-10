package middleware

import (
	"net/http/httptest"
	"testing"

	"github.com/gofiber/fiber/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestAuthMiddleware_MissingHeader(t *testing.T) {
	app := fiber.New()

	app.Use(AuthMiddleware())
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("protected")
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestAuthMiddleware_ValidHeader(t *testing.T) {
	app := fiber.New()

	app.Use(AuthMiddleware())
	app.Get("/protected", func(c *fiber.Ctx) error {
		userID := c.Locals("userID")
		return c.JSON(fiber.Map{"userID": userID})
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("X-User-Id", "user-123")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestAuthMiddleware_EmptyHeader(t *testing.T) {
	app := fiber.New()

	app.Use(AuthMiddleware())
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("protected")
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("X-User-Id", "")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestAuthMiddleware_SetsLocals(t *testing.T) {
	app := fiber.New()

	app.Use(AuthMiddleware())
	app.Get("/protected", func(c *fiber.Ctx) error {
		userID := c.Locals("userID")
		require.NotNil(t, userID)
		return c.SendString("Hello " + userID.(string))
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("X-User-Id", "test-user-id")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestAuthMiddleware_Chain(t *testing.T) {
	app := fiber.New()

	// Multiple middleware
	app.Use(AuthMiddleware())
	app.Use(func(c *fiber.Ctx) error {
		userID := c.Locals("userID")
		if userID == nil {
			return c.Status(500).SendString("userID not set")
		}
		return c.Next()
	})

	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("access granted")
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("X-User-Id", "user-456")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}
