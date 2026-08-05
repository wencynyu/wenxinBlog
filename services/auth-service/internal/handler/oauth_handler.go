package handler

import (
	"strings"

	"wenxinblog/auth-service/internal/dto"
	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
)

type OAuthHandler struct {
	oauthService *service.OAuthService
	frontendURL  string // 中间码 302 回前端的目标
}

func NewOAuthHandler(oauthService *service.OAuthService, frontendURL string) *OAuthHandler {
	return &OAuthHandler{oauthService: oauthService, frontendURL: strings.TrimRight(frontendURL, "/")}
}

// OAuthInitiate GET /api/v1/auth/oauth/:provider —— 登录发起。
// 生成 state（存 Redis）+ oauth_state cookie（SameSite=Lax 防 login-CSRF），302 到 provider。
func (h *OAuthHandler) OAuthInitiate(c *fiber.Ctx) error {
	provider := c.Params("provider")
	state, authURL, err := h.oauthService.InitiateLogin(c.Context(), provider)
	if err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	c.Cookie(&fiber.Cookie{
		Name:     "oauth_state",
		Value:    state,
		MaxAge:   600, // 10 分钟，与 state TTL 对齐
		SameSite: "Lax",
		HTTPOnly: true,
		Path:     "/",
	})
	return c.Redirect(authURL)
}

// OAuthCallback GET /api/v1/auth/callback/:provider —— provider 回调。
// 校验 state（Redis 一次性 + login 意图校验 cookie）→ 换码/创建/绑定 → 签发中间码 → 302 回前端。
func (h *OAuthHandler) OAuthCallback(c *fiber.Ctx) error {
	provider := c.Params("provider")
	code := c.Query("code")
	state := c.Query("state")
	if code == "" || state == "" {
		return h.redirectError(c, provider, "missing_code_or_state")
	}
	cookieState := c.Cookies("oauth_state")
	intermediate, err := h.oauthService.HandleCallback(c.Context(), provider, code, state, cookieState)
	if err != nil {
		return h.redirectError(c, provider, errorReason(err))
	}
	return c.Redirect(h.frontendURL + "/auth/callback/" + provider + "?code=" + intermediate)
}

// OAuthExchange POST /api/v1/auth/oauth/exchange —— 用中间码兑换登录态。
func (h *OAuthHandler) OAuthExchange(c *fiber.Ctx) error {
	var req dto.OAuthExchangeRequest
	if err := c.BodyParser(&req); err != nil || req.Code == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "code is required"})
	}
	res, err := h.oauthService.ExchangeIntermediate(c.Context(), req.Code)
	if err != nil {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: err.Error()})
	}
	if res.Outcome == "link" {
		return c.JSON(dto.APIResponse{Code: 200, Message: "linked", Data: dto.OAuthExchangeResponse{
			Outcome: "link", Provider: res.Provider,
		}})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "login success", Data: dto.OAuthExchangeResponse{
		Outcome: "login",
		User:    toUserResponse(res.User),
		Tokens:  &dto.TokenResponse{AccessToken: res.Tokens.AccessToken, RefreshToken: res.Tokens.RefreshToken, ExpiresIn: res.Tokens.ExpiresIn},
	}})
}

// --- 以下为鉴权路由（/api/v1/account/**，网关注入 X-User-Id） ---

// OAuthLink POST /api/v1/account/oauth/:provider/link —— 已登录用户发起绑定。
func (h *OAuthHandler) OAuthLink(c *fiber.Ctx) error {
	userID := c.Get("X-User-Id")
	if userID == "" {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "unauthorized"})
	}
	provider := c.Params("provider")
	_, authURL, err := h.oauthService.InitiateLink(c.Context(), userID, provider)
	if err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: dto.OAuthLinkResponse{AuthURL: authURL}})
}

// OAuthUnlink DELETE /api/v1/account/oauth/:provider —— 解绑。
func (h *OAuthHandler) OAuthUnlink(c *fiber.Ctx) error {
	userID := c.Get("X-User-Id")
	if userID == "" {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "unauthorized"})
	}
	if err := h.oauthService.Unlink(c.Context(), userID, c.Params("provider")); err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "unlinked"})
}

// OAuthList GET /api/v1/account/oauth —— 列出当前用户已绑定的 provider。
func (h *OAuthHandler) OAuthList(c *fiber.Ctx) error {
	userID := c.Get("X-User-Id")
	if userID == "" {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "unauthorized"})
	}
	accounts, err := h.oauthService.ListLinked(c.Context(), userID)
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	items := make([]dto.OAuthAccountItem, 0, len(accounts))
	for _, a := range accounts {
		items = append(items, dto.OAuthAccountItem{Provider: a.Provider, ProviderUserID: a.ProviderUserID, LinkedAt: a.CreatedAt})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: fiber.Map{"items": items}})
}

// --- helpers ---

func (h *OAuthHandler) redirectError(c *fiber.Ctx, provider, reason string) error {
	return c.Redirect(h.frontendURL + "/auth/callback/" + provider + "?error=" + reason)
}

func errorReason(err error) string {
	switch err {
	case service.ErrInvalidOAuthState:
		return "invalid_state"
	case service.ErrUnsupportedProvider:
		return "unsupported_provider"
	default:
		return "provider_error"
	}
}

func toUserResponse(u *model.User) *dto.UserResponse {
	if u == nil {
		return nil
	}
	return &dto.UserResponse{
		ID: u.ID, Username: u.Username, Email: u.Email, AvatarURL: u.AvatarURL,
		Status: u.Status, TwoFAEnabled: u.TwoFAEnabled,
	}
}

// RegisterOAuthRoutes 注册 OAuth 路由：
//
//	/api/v1/auth/**    —— 公开（网关白名单，不注入身份）
//	/api/v1/account/** —— 鉴权（网关 AuthenticationFilter 注入 X-User-Id）
func RegisterOAuthRoutes(api fiber.Router, oauthService *service.OAuthService, frontendURL string) {
	h := NewOAuthHandler(oauthService, frontendURL)

	auth := api.Group("/auth")
	auth.Get("/oauth/:provider", h.OAuthInitiate)
	auth.Get("/callback/:provider", h.OAuthCallback)
	auth.Post("/oauth/exchange", h.OAuthExchange)

	account := api.Group("/account")
	account.Post("/oauth/:provider/link", h.OAuthLink)
	account.Delete("/oauth/:provider", h.OAuthUnlink)
	account.Get("/oauth", h.OAuthList)
}
