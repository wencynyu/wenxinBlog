package handler

import (
	"wenxinblog/auth-service/internal/middleware"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
)

// CookieOptions 由 config 注入：生产 HTTPS 置 Secure=true；
// 前后端跨子域部署（api.xxx.com ↔ www.xxx.com）需配 Domain=".xxx.com"。
type CookieOptions struct {
	Secure bool
	Domain string
}

// MaxAge 与 jwt_service.go 的 token TTL 对齐（access 15min / refresh 7d）。
const (
	accessCookieMaxAge  = 15 * 60
	refreshCookieMaxAge = 7 * 24 * 3600
)

// setAuthCookies 登录/续期成功后签发 httpOnly 会话 cookie（Web 端）。
// JSON body 里的 tokens 仍然返回：Mobile 端继续走 Authorization Bearer（双轨）。
func setAuthCookies(c *fiber.Ctx, tokens *service.TokenPair, opts CookieOptions) {
	c.Cookie(&fiber.Cookie{
		Name:     middleware.AccessTokenCookieName,
		Value:    tokens.AccessToken,
		Path:     "/",
		MaxAge:   accessCookieMaxAge,
		HTTPOnly: true,
		SameSite: "Lax",
		Secure:   opts.Secure,
		Domain:   opts.Domain,
	})
	// refresh cookie 限定 /api/v1/auth 前缀：只需被 /auth/refresh 读到，缩小暴露面。
	c.Cookie(&fiber.Cookie{
		Name:     middleware.RefreshTokenCookieName,
		Value:    tokens.RefreshToken,
		Path:     "/api/v1/auth",
		MaxAge:   refreshCookieMaxAge,
		HTTPOnly: true,
		SameSite: "Lax",
		Secure:   opts.Secure,
		Domain:   opts.Domain,
	})
}

// clearAuthCookies 登出时过期两个会话 cookie（httpOnly，客户端 JS 无法自行清除）。
func clearAuthCookies(c *fiber.Ctx, opts CookieOptions) {
	c.Cookie(&fiber.Cookie{
		Name: middleware.AccessTokenCookieName, Value: "",
		Path: "/", MaxAge: -1, HTTPOnly: true, SameSite: "Lax",
		Secure: opts.Secure, Domain: opts.Domain,
	})
	c.Cookie(&fiber.Cookie{
		Name: middleware.RefreshTokenCookieName, Value: "",
		Path: "/api/v1/auth", MaxAge: -1, HTTPOnly: true, SameSite: "Lax",
		Secure: opts.Secure, Domain: opts.Domain,
	})
}
