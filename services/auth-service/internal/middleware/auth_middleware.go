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

// Web 会话 cookie 名：auth-service 签发（handler/cookies.go），gateway 的
// AuthenticationFilter 与本中间件按同名读取。Bearer header 优先，cookie 兜底。
const (
	AccessTokenCookieName  = "access_token"
	RefreshTokenCookieName = "refresh_token"
)

func AuthMiddleware(authService TokenValidator) fiber.Handler {
	return func(c *fiber.Ctx) error {
		token := extractBearerOrCookie(c)
		if token == "" {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "missing bearer token or session cookie"})
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

// extractBearerOrCookie 从 Authorization: Bearer 取 token；
// 无 header 时兜底读 httpOnly 会话 cookie（Web 端），仍无则返回空串。
func extractBearerOrCookie(c *fiber.Ctx) string {
	authHeader := c.Get("Authorization")
	if authHeader != "" {
		token := strings.TrimPrefix(authHeader, "Bearer ")
		if token != authHeader {
			return token
		}
		return "" // header 存在但非 Bearer 格式，视为无凭证
	}
	return c.Cookies(AccessTokenCookieName)
}
