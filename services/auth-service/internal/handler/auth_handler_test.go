package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http/httptest"
	"testing"

	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type mockAuthService struct {
	registerFunc      func(ctx context.Context, email, username, password string) (*model.User, error)
	loginFunc         func(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error)
	validateTokenFunc func(token string) (*service.Claims, error)
	refreshTokenFunc  func(token string) (*service.TokenPair, error)
	getUserByIDFunc   func(ctx context.Context, id string) (*model.User, error)
}

func (m *mockAuthService) Register(ctx context.Context, email, username, password string) (*model.User, error) {
	if m.registerFunc != nil {
		return m.registerFunc(ctx, email, username, password)
	}
	return nil, nil
}

func (m *mockAuthService) Login(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error) {
	if m.loginFunc != nil {
		return m.loginFunc(ctx, email, password)
	}
	return nil, nil, nil
}

func (m *mockAuthService) ValidateToken(token string) (*service.Claims, error) {
	if m.validateTokenFunc != nil {
		return m.validateTokenFunc(token)
	}
	return nil, nil
}

func (m *mockAuthService) RefreshToken(token string) (*service.TokenPair, error) {
	if m.refreshTokenFunc != nil {
		return m.refreshTokenFunc(token)
	}
	return nil, nil
}

func (m *mockAuthService) GetUserByID(ctx context.Context, id string) (*model.User, error) {
	if m.getUserByIDFunc != nil {
		return m.getUserByIDFunc(ctx, id)
	}
	return nil, nil
}

func setupApp(svc service.AuthServicer) *fiber.App {
	app := fiber.New(fiber.Config{DisableStartupMessage: true, AppName: "Test"})
	h := NewAuthHandler(svc)
	app.Post("/register", h.Register)
	app.Post("/login", h.Login)
	app.Post("/refresh", h.RefreshToken)
	app.Post("/logout", h.Logout)
	app.Get("/me", func(c *fiber.Ctx) error {
		c.Locals("userId", "test-user-id")
		return h.GetCurrentUser(c)
	})
	return app
}

func TestRegister_InvalidBody(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader([]byte("invalid json")))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRegister_Success(t *testing.T) {
	svc := &mockAuthService{
		registerFunc: func(ctx context.Context, email, username, password string) (*model.User, error) {
			return &model.User{ID: "user-1", Email: email, Username: username, Status: "ACTIVE"}, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 201, resp.StatusCode)
}

func TestRegister_UserExists(t *testing.T) {
	svc := &mockAuthService{
		registerFunc: func(ctx context.Context, email, username, password string) (*model.User, error) {
			return nil, service.ErrUserExists
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 409, resp.StatusCode)
}

func TestRegister_InternalError(t *testing.T) {
	svc := &mockAuthService{
		registerFunc: func(ctx context.Context, email, username, password string) (*model.User, error) {
			return nil, errors.New("db error")
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestRegister_WeakPassword(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "123"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRegister_InvalidEmail(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "not-an-email", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRegister_EmptyUsername(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestLogin_InvalidBody(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("POST", "/login", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestLogin_Success(t *testing.T) {
	jwtSvc := service.NewJWTService("test-secret")
	tokens, _ := jwtSvc.GenerateTokenPair("user-123", []string{"USER"})
	svc := &mockAuthService{
		loginFunc: func(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error) {
			return tokens, &model.User{ID: "user-123", Email: email, Username: "testuser", Status: "ACTIVE"}, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "password": "123456"})
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestLogin_InvalidCredentials(t *testing.T) {
	svc := &mockAuthService{
		loginFunc: func(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error) {
			return nil, nil, service.ErrInvalidCredentials
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "password": "wrong"})
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestLogin_InvalidEmail(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "not-an-email", "password": "12345678"})
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRefreshToken_Success(t *testing.T) {
	jwtSvc := service.NewJWTService("test-secret")
	tokens, _ := jwtSvc.GenerateTokenPair("user-123", []string{"USER"})
	svc := &mockAuthService{
		refreshTokenFunc: func(token string) (*service.TokenPair, error) {
			return tokens, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"refreshToken": tokens.RefreshToken})
	req := httptest.NewRequest("POST", "/refresh", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestRefreshToken_Invalid(t *testing.T) {
	svc := &mockAuthService{
		refreshTokenFunc: func(token string) (*service.TokenPair, error) {
			return nil, errors.New("invalid token")
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"refreshToken": "bad-token"})
	req := httptest.NewRequest("POST", "/refresh", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestGetMe_Success(t *testing.T) {
	svc := &mockAuthService{
		getUserByIDFunc: func(ctx context.Context, id string) (*model.User, error) {
			return &model.User{ID: id, Email: "a@b.com", Username: "testuser", Status: "ACTIVE"}, nil
		},
	}
	app := setupApp(svc)

	req := httptest.NewRequest("GET", "/me", nil)
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestLogout(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("POST", "/logout", nil)
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}
