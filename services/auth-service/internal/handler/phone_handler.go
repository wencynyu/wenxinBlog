package handler

import (
	"errors"

	"wenxinblog/auth-service/internal/dto"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
)

type PhoneHandler struct {
	phoneService *service.PhoneService
}

func NewPhoneHandler(phoneService *service.PhoneService) *PhoneHandler {
	return &PhoneHandler{phoneService: phoneService}
}

// SendCode POST /api/v1/auth/phone/send-code
// 成功与限流都返回相同 200 通用消息（不泄露号码是否注册/是否触发限流）；
// 仅格式非法返回 400，发送层故障返回 500。
func (h *PhoneHandler) SendCode(c *fiber.Ctx) error {
	var req dto.PhoneSendCodeRequest
	if err := c.BodyParser(&req); err != nil || req.Phone == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "phone is required"})
	}
	err := h.phoneService.SendCode(c.Context(), req.Phone, c.IP())
	switch {
	case errors.Is(err, service.ErrInvalidPhone):
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	case errors.Is(err, service.ErrRateLimited):
		return c.JSON(dto.APIResponse{Code: 200, Message: "if allowed, sms sent"}) // 静默限流
	case err != nil:
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "sms send failed"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "if allowed, sms sent"})
}

// Login POST /api/v1/auth/phone/login —— 校验验证码并签发 token（与密码登录同响应形状）。
func (h *PhoneHandler) Login(c *fiber.Ctx) error {
	var req dto.PhoneLoginRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if req.Phone == "" || req.Code == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "phone and code are required"})
	}
	tokens, user, err := h.phoneService.Login(c.Context(), req.Phone, req.Code)
	if err != nil {
		if errors.Is(err, service.ErrInvalidCredentials) {
			return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "invalid phone or code"})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "login success", Data: dto.AuthResponse{
		User:   toUserResponse(user),
		Tokens: &dto.TokenResponse{AccessToken: tokens.AccessToken, RefreshToken: tokens.RefreshToken, ExpiresIn: tokens.ExpiresIn},
	}})
}

// RegisterPhoneRoutes 注册手机号验证码登录路由（/api/v1/auth/**，网关白名单公开）。
func RegisterPhoneRoutes(api fiber.Router, phoneService *service.PhoneService) {
	h := NewPhoneHandler(phoneService)
	auth := api.Group("/auth")
	auth.Post("/phone/send-code", h.SendCode)
	auth.Post("/phone/login", h.Login)
}
