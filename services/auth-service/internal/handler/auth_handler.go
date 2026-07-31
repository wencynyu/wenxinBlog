package handler

import (
	"errors"
	"strings"

	"wenxinblog/auth-service/internal/dto"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
)

type AuthHandler struct {
	authService service.AuthServicer
}

func NewAuthHandler(authService service.AuthServicer) *AuthHandler {
	return &AuthHandler{authService: authService}
}

func (h *AuthHandler) Register(c *fiber.Ctx) error {
	var req dto.RegisterRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if err := validateRegisterRequest(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	user, err := h.authService.Register(c.Context(), req.Email, req.Username, req.Password)
	if err != nil {
		if err == service.ErrUserExists {
			return c.Status(409).JSON(dto.ErrorResponse{Code: 409, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.Status(201).JSON(dto.APIResponse{Code: 201, Message: "registered", Data: dto.UserResponse{
		ID: user.ID, Username: user.Username, Email: user.Email, Status: user.Status,
	}})
}

func (h *AuthHandler) Login(c *fiber.Ctx) error {
	var req dto.LoginRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if err := validateLoginRequest(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	tokens, user, err := h.authService.Login(c.Context(), req.Email, req.Password)
	if err != nil {
		if err == service.ErrInvalidCredentials {
			return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "login success", Data: dto.AuthResponse{
		User:   &dto.UserResponse{ID: user.ID, Username: user.Username, Email: user.Email, Status: user.Status},
		Tokens: &dto.TokenResponse{AccessToken: tokens.AccessToken, RefreshToken: tokens.RefreshToken, ExpiresIn: tokens.ExpiresIn},
	}})
}

func (h *AuthHandler) RefreshToken(c *fiber.Ctx) error {
	var req dto.RefreshRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	tokens, err := h.authService.RefreshToken(req.RefreshToken)
	if err != nil {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "invalid refresh token"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "token refreshed", Data: dto.TokenResponse{
		AccessToken: tokens.AccessToken, RefreshToken: tokens.RefreshToken, ExpiresIn: tokens.ExpiresIn,
	}})
}

func (h *AuthHandler) GetCurrentUser(c *fiber.Ctx) error {
	userID := c.Locals("userId").(string)
	user, err := h.authService.GetUserByID(c.Context(), userID)
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: dto.UserResponse{
		ID: user.ID, Username: user.Username, Email: user.Email, AvatarURL: user.AvatarURL,
		Status: user.Status, TwoFAEnabled: user.TwoFAEnabled,
	}})
}

func (h *AuthHandler) Logout(c *fiber.Ctx) error {
	return c.JSON(dto.APIResponse{Code: 200, Message: "logged out"})
}

// ValidateToken 验证 Bearer token，返回 userId + roles。
// 供网关 AuthenticationFilter 调用（GET /api/v1/auth/validate）。
func (h *AuthHandler) ValidateToken(c *fiber.Ctx) error {
	authHeader := c.Get("Authorization")
	if len(authHeader) <= 7 || authHeader[:7] != "Bearer " {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "missing bearer token"})
	}
	token := authHeader[7:]
	claims, err := h.authService.ValidateToken(token)
	if err != nil || claims == nil {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "invalid token"})
	}
	return c.JSON(fiber.Map{
		"userId": claims.UserID,
		"email":  "",
		"roles":  claims.Roles,
	})
}

// validateRegisterRequest 校验注册请求：email 非空且含 @、username 非空、password 长度 >= 8。
func validateRegisterRequest(req *dto.RegisterRequest) error {
	if err := validateEmail(req.Email); err != nil {
		return err
	}
	if strings.TrimSpace(req.Username) == "" {
		return errors.New("username is required")
	}
	if len(req.Password) < 8 {
		return errors.New("password must be at least 8 characters")
	}
	return nil
}

// validateLoginRequest 校验登录请求：email 非空且含 @、password 非空。
func validateLoginRequest(req *dto.LoginRequest) error {
	if err := validateEmail(req.Email); err != nil {
		return err
	}
	if req.Password == "" {
		return errors.New("password is required")
	}
	return nil
}

func validateEmail(email string) error {
	if strings.TrimSpace(email) == "" || !strings.Contains(email, "@") {
		return errors.New("invalid email")
	}
	return nil
}

func RegisterRoutes(api fiber.Router, authService service.AuthServicer) {
	h := NewAuthHandler(authService)
	auth := api.Group("/auth")
	auth.Post("/register", h.Register)
	auth.Post("/login", h.Login)
	auth.Post("/refresh", h.RefreshToken)
	auth.Post("/logout", h.Logout)
	auth.Get("/validate", h.ValidateToken)
}
