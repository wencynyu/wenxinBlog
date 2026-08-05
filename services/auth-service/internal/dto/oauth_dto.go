package dto

import "time"

// OAuthLinkResponse 绑定发起：返回 provider 授权 URL，前端随后跳转过去。
type OAuthLinkResponse struct {
	AuthURL string `json:"authUrl"`
}

// OAuthExchangeRequest 用一次性中间码兑换登录态。
type OAuthExchangeRequest struct {
	Code string `json:"code"`
}

// OAuthExchangeResponse 中间码兑换结果。
// outcome=login → user+tokens；outcome=link → 仅 provider（前端刷新绑定列表）。
type OAuthExchangeResponse struct {
	Outcome  string         `json:"outcome"`
	User     *UserResponse  `json:"user,omitempty"`
	Tokens   *TokenResponse `json:"tokens,omitempty"`
	Provider string         `json:"provider,omitempty"`
}

// OAuthAccountItem 绑定列表项（不含 token 等敏感字段）。
type OAuthAccountItem struct {
	Provider       string    `json:"provider"`
	ProviderUserID string    `json:"providerUserId"`
	LinkedAt       time.Time `json:"linkedAt"`
}
