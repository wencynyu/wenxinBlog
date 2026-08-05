package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// ProviderUserInfo 是从第三方 provider 换取后的标准化用户信息。
type ProviderUserInfo struct {
	Provider   string // "google"
	ProviderID string // Google sub
	Email      string
	Name       string
	AvatarURL  string
}

// OAuthProvider 抽象第三方登录 provider（手写 net/http，不引 oauth2 库）。
type OAuthProvider interface {
	Name() string
	// AuthURL 返回引导用户授权的 URL（含 state）。
	AuthURL(state string) string
	// Exchange 用授权码换取 access_token 并拉取用户信息。
	Exchange(ctx context.Context, code string) (*ProviderUserInfo, error)
}

// ============================== Google ==============================

type GoogleProvider struct {
	clientID     string
	clientSecret string
	redirectURL  string
	client       *http.Client
	authURL      string
	tokenURL     string
	userInfoURL  string
}

func NewGoogleProvider(clientID, clientSecret, redirectURL string) *GoogleProvider {
	return &GoogleProvider{
		clientID:     clientID,
		clientSecret: clientSecret,
		redirectURL:  redirectURL,
		client:       &http.Client{Timeout: 10 * time.Second},
		authURL:      "https://accounts.google.com/o/oauth2/v2/auth",
		tokenURL:     "https://oauth2.googleapis.com/token",
		userInfoURL:  "https://openidconnect.googleapis.com/v1/userinfo",
	}
}

func (p *GoogleProvider) Name() string { return "google" }

func (p *GoogleProvider) AuthURL(state string) string {
	q := url.Values{}
	q.Set("client_id", p.clientID)
	q.Set("redirect_uri", p.redirectURL)
	q.Set("response_type", "code")
	q.Set("scope", "openid email profile")
	q.Set("state", state)
	return p.authURL + "?" + q.Encode()
}

func (p *GoogleProvider) Exchange(ctx context.Context, code string) (*ProviderUserInfo, error) {
	form := url.Values{}
	form.Set("grant_type", "authorization_code")
	form.Set("code", code)
	form.Set("client_id", p.clientID)
	form.Set("client_secret", p.clientSecret)
	form.Set("redirect_uri", p.redirectURL)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("google token exchange: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("google token exchange: status %d body=%s", resp.StatusCode, string(b))
	}
	var tok struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&tok); err != nil {
		return nil, fmt.Errorf("google token decode: %w", err)
	}
	if tok.AccessToken == "" {
		return nil, fmt.Errorf("google token exchange: empty access_token")
	}

	// 拉取用户信息
	infoReq, _ := http.NewRequestWithContext(ctx, http.MethodGet, p.userInfoURL, nil)
	infoReq.Header.Set("Authorization", "Bearer "+tok.AccessToken)
	infoResp, err := p.client.Do(infoReq)
	if err != nil {
		return nil, fmt.Errorf("google userinfo: %w", err)
	}
	defer infoResp.Body.Close()
	if infoResp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(infoResp.Body)
		return nil, fmt.Errorf("google userinfo: status %d body=%s", infoResp.StatusCode, string(b))
	}
	var ui struct {
		Sub      string `json:"sub"`
		Email    string `json:"email"`
		Name     string `json:"name"`
		Picture  string `json:"picture"`
		Verified bool   `json:"email_verified"`
	}
	if err := json.NewDecoder(infoResp.Body).Decode(&ui); err != nil {
		return nil, fmt.Errorf("google userinfo decode: %w", err)
	}
	if ui.Sub == "" {
		return nil, fmt.Errorf("google userinfo: missing sub")
	}
	email := ui.Email
	if !ui.Verified {
		// 未验证邮箱不作为账号标识（仅记录）；find-or-create 仍按 provider id 走。
		email = ""
	}
	return &ProviderUserInfo{
		Provider: "google", ProviderID: ui.Sub, Email: email,
		Name: ui.Name, AvatarURL: ui.Picture,
	}, nil
}
