package middleware

import (
	"errors"
	"net/http/httptest"
	"testing"

	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type mockAuthMiddlewareService struct {
	validateTokenFunc func(token string) (*service.Claims, error)
}

func (m *mockAuthMiddlewareService) ValidateToken(token string) (*service.Claims, error) {
	if m.validateTokenFunc != nil {
		return m.validateTokenFunc(token)
	}
	return &service.Claims{UserID: "user-123", Roles: []string{"USER"}}, nil
}

func TestAuthMiddleware_MissingHeader(t *testing.T) {
	svc := &mockAuthMiddlewareService{}
	app := fiber.New()
	app.Use(AuthMiddleware(svc))
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("ok")
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestAuthMiddleware_InvalidFormat(t *testing.T) {
	svc := &mockAuthMiddlewareService{}
	app := fiber.New()
	app.Use(AuthMiddleware(svc))
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("ok")
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "InvalidFormat token")
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestAuthMiddleware_ValidToken(t *testing.T) {
	jwtSvc := service.NewJWTService("test-secret")
	tokens, err := jwtSvc.GenerateTokenPair("user-123", []string{"USER"})
	require.NoError(t, err)

	svc := &mockAuthMiddlewareService{}
	app := fiber.New()
	app.Use(AuthMiddleware(svc))
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("Hello " + c.Locals("userId").(string))
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "Bearer "+tokens.AccessToken)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestAuthMiddleware_InvalidToken(t *testing.T) {
	svc := &mockAuthMiddlewareService{
		validateTokenFunc: func(token string) (*service.Claims, error) {
			return nil, errors.New("invalid token")
		},
	}
	app := fiber.New()
	app.Use(AuthMiddleware(svc))
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.SendString("ok")
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "Bearer bad-token")
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestAuthMiddleware_SetsLocals(t *testing.T) {
	svc := &mockAuthMiddlewareService{
		validateTokenFunc: func(token string) (*service.Claims, error) {
			return &service.Claims{UserID: "user-456", Roles: []string{"USER", "ADMIN"}}, nil
		},
	}
	app := fiber.New()
	app.Use(AuthMiddleware(svc))
	app.Get("/protected", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"userId": c.Locals("userId"),
			"roles":  c.Locals("roles"),
		})
	})

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "Bearer some-token")
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}
