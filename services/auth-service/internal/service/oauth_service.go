package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"log"
	"math/big"
	"regexp"
	"strings"
	"time"

	"wenxinblog/auth-service/internal/cache"
	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/repository"
)

var (
	ErrUnsupportedProvider = errors.New("unsupported oauth provider")
	ErrInvalidOAuthState   = errors.New("invalid or expired oauth state")
	ErrInvalidOAuthCode    = errors.New("invalid or expired oauth code")
	ErrOAuthAlreadyLinked  = errors.New("third-party account already linked to another user")
)

// ExchangeResult 是中间码兑换结果：login 返回 user+tokens，link 仅返回 provider。
type ExchangeResult struct {
	Outcome    string      `json:"outcome"` // "login" | "link"
	User       *model.User `json:"-"`
	Tokens     *TokenPair  `json:"-"`
	Provider   string      `json:"provider,omitempty"`
	LinkUserID string      `json:"userId,omitempty"` // link 时回填（便于前端刷新）
}

// OAuthStateTTL / IntermediateCodeTTL
const (
	oauthStateTTL        = 10 * time.Minute
	oauthIntermediateTTL = 30 * time.Second
)

type OAuthService struct {
	auth      *AuthService
	oauthRepo repository.OAuthAccountRepository
	cache     *cache.Cache
	providers map[string]OAuthProvider // key = provider name
}

func NewOAuthService(auth *AuthService, oauthRepo repository.OAuthAccountRepository, cache *cache.Cache, providers map[string]OAuthProvider) *OAuthService {
	return &OAuthService{auth: auth, oauthRepo: oauthRepo, cache: cache, providers: providers}
}

// HasProvider 判断是否配置了某个 provider（main.go 启动日志/前端发现用）。
func (s *OAuthService) HasProvider(name string) bool {
	if p, ok := s.providers[name]; ok {
		return p != nil
	}
	return false
}

// InitiateLogin 生成登录用 state 并返回 provider 授权 URL。
func (s *OAuthService) InitiateLogin(ctx context.Context, provider string) (state, authURL string, err error) {
	p := s.providers[provider]
	if p == nil {
		return "", "", ErrUnsupportedProvider
	}
	state = randomToken()
	if err := s.cache.SaveOAuthState(ctx, state, cache.OAuthState{Intent: "login", Provider: provider}, oauthStateTTL); err != nil {
		return "", "", err
	}
	return state, p.AuthURL(state), nil
}

// InitiateLink 为已登录用户生成绑定用 state（intent=link:<userID>），返回 provider 授权 URL。
func (s *OAuthService) InitiateLink(ctx context.Context, userID, provider string) (state, authURL string, err error) {
	p := s.providers[provider]
	if p == nil {
		return "", "", ErrUnsupportedProvider
	}
	state = randomToken()
	if err := s.cache.SaveOAuthState(ctx, state, cache.OAuthState{Intent: "link:" + userID, Provider: provider}, oauthStateTTL); err != nil {
		return "", "", err
	}
	return state, p.AuthURL(state), nil
}

// HandleCallback 处理 provider 回调：校验一次性 state → 换取用户信息 →
// login 意图走 find-or-create，link 意图走绑定 → 签发一次性中间码返回。
// cookieState 是浏览器 oauth_state cookie 的值：login 意图下必须与 state 一致
// （防 login-CSRF：攻击者无法在自己的浏览器上为受害者发起登录）。
func (s *OAuthService) HandleCallback(ctx context.Context, provider, code, state, cookieState string) (string, error) {
	st, err := s.cache.ConsumeOAuthState(ctx, state)
	if err != nil || st == nil {
		log.Printf("oauth callback %s: state NOT found/expired (state=%q cookie=%q)", provider, state, cookieState)
		return "", ErrInvalidOAuthState
	}
	if st.Provider != provider {
		log.Printf("oauth callback %s: provider mismatch (state.provider=%s)", provider, st.Provider)
		return "", ErrInvalidOAuthState
	}
	if st.Intent == "login" && cookieState != state {
		log.Printf("oauth callback %s: login-CSRF cookie mismatch (state=%q cookie=%q)", provider, state, cookieState)
		return "", ErrInvalidOAuthState
	}
	p := s.providers[provider]
	if p == nil {
		return "", ErrUnsupportedProvider
	}
	info, err := p.Exchange(ctx, code)
	if err != nil {
		log.Printf("oauth callback %s: exchange failed (code len=%d): %v", provider, len(code), err)
		return "", err
	}
	log.Printf("oauth callback %s: exchange OK providerID=%s email=%q name=%q", provider, info.ProviderID, info.Email, info.Name)

	var ic cache.IntermediateCode
	if strings.HasPrefix(st.Intent, "link:") {
		userID := strings.TrimPrefix(st.Intent, "link:")
		if err := s.linkAccount(ctx, userID, info); err != nil {
			log.Printf("oauth callback %s: link failed: %v", provider, err)
			return "", err
		}
		ic = cache.IntermediateCode{Outcome: "link", UserID: userID, Provider: provider}
		log.Printf("oauth callback %s: linked to user %s", provider, userID)
	} else {
		user, err := s.loginOrCreate(ctx, info)
		if err != nil {
			log.Printf("oauth callback %s: loginOrCreate failed: %v", provider, err)
			return "", err
		}
		ic = cache.IntermediateCode{Outcome: "login", UserID: user.ID}
		log.Printf("oauth callback %s: user resolved id=%s username=%s", provider, user.ID, user.Username)
	}

	intermediate := randomToken()
	if err := s.cache.SaveIntermediateCode(ctx, intermediate, ic, oauthIntermediateTTL); err != nil {
		log.Printf("oauth callback %s: save intermediate failed: %v", provider, err)
		return "", err
	}
	log.Printf("oauth callback %s: ✔ intermediate issued (outcome=%s), redirecting to frontend", provider, ic.Outcome)
	return intermediate, nil
}

// ExchangeIntermediate 兑换中间码：login→签发 token 对；link→仅返回 provider。
func (s *OAuthService) ExchangeIntermediate(ctx context.Context, code string) (*ExchangeResult, error) {
	ic, err := s.cache.ConsumeIntermediateCode(ctx, code)
	if err != nil || ic == nil {
		log.Printf("oauth exchange: intermediate NOT found/expired (code=%q)", code)
		return nil, ErrInvalidOAuthCode
	}
	log.Printf("oauth exchange: consumed outcome=%s userID=%s", ic.Outcome, ic.UserID)
	switch ic.Outcome {
	case "link":
		return &ExchangeResult{Outcome: "link", Provider: ic.Provider, LinkUserID: ic.UserID}, nil
	case "login":
		user, err := s.auth.userRepo.FindByID(ctx, ic.UserID)
		if err != nil || user == nil {
			log.Printf("oauth exchange: user not found %s", ic.UserID)
			return nil, ErrInvalidCredentials
		}
		if user.Status != "ACTIVE" {
			log.Printf("oauth exchange: user not active %s status=%s", ic.UserID, user.Status)
			return nil, ErrInvalidCredentials
		}
		tokens, err := s.auth.issueTokens(ctx, user.ID)
		if err != nil {
			log.Printf("oauth exchange: issueTokens failed: %v", err)
			return nil, err
		}
		log.Printf("oauth exchange: ✔ LOGIN SUCCESS user=%s tokens issued", user.ID)
		return &ExchangeResult{Outcome: "login", User: user, Tokens: tokens}, nil
	}
	return nil, ErrInvalidOAuthCode
}

// loginOrCreate 解析社交登录用户：
//  1. (provider, provider_user_id) 已绑定 → 直接登录该用户；
//  2. 邮箱已验证（info.Email 非空，provider 只在 verified 时回填）且命中已有账号 → 自动绑定到该账号并登录（不再创建重复账号，避免 users_email_unique 冲突）；
//  3. 都没有 → 创建独立新账号。
func (s *OAuthService) loginOrCreate(ctx context.Context, info *ProviderUserInfo) (*model.User, error) {
	acc, err := s.oauthRepo.FindByProvider(ctx, info.Provider, info.ProviderID)
	if err != nil {
		return nil, err
	}
	if acc != nil {
		user, err := s.auth.userRepo.FindByID(ctx, acc.UserID)
		if err != nil || user == nil {
			return nil, ErrInvalidCredentials
		}
		if user.Status != "ACTIVE" {
			return nil, ErrInvalidCredentials
		}
		return user, nil
	}

	// 邮箱已验证且与已有账号一致 → 自动绑定并登录（无需新建，避免唯一约束冲突）。
	if info.Email != "" {
		existing, err := s.auth.userRepo.FindByEmail(ctx, info.Email)
		if err != nil {
			return nil, err
		}
		if existing != nil {
			if existing.Status != "ACTIVE" {
				return nil, ErrInvalidCredentials
			}
			if err := s.oauthRepo.Create(ctx, &model.OAuthAccount{
				UserID: existing.ID, Provider: info.Provider, ProviderUserID: info.ProviderID,
			}); err != nil {
				// 并发重复绑定时可能撞 UNIQUE(provider,provider_user_id)；不阻断登录。
				log.Printf("oauth: auto-link binding for user %s: %v", existing.ID, err)
			}
			log.Printf("oauth: auto-linked %s to existing user %s (verified email match)", info.Provider, existing.ID)
			return existing, nil
		}
	}

	// 都没有 → 创建新用户
	username, err := generateUniqueUsername(ctx, s.auth.userRepo, info.Name, info.Provider)
	if err != nil {
		return nil, err
	}
	user := &model.User{Username: username, Email: info.Email, AvatarURL: info.AvatarURL}
	if err := s.auth.userRepo.CreateNoPassword(ctx, user); err != nil {
		return nil, err
	}
	if s.auth.roleRepo != nil {
		if err := s.auth.roleRepo.AssignRole(ctx, user.ID, "user"); err != nil {
			log.Printf("assign default role failed for oauth user %s: %v", user.ID, err)
		}
	}
	if err := s.oauthRepo.Create(ctx, &model.OAuthAccount{
		UserID: user.ID, Provider: info.Provider, ProviderUserID: info.ProviderID,
	}); err != nil {
		log.Printf("create oauth_account failed for user %s: %v", user.ID, err)
	}
	if s.auth.userSync != nil {
		if err := s.auth.userSync.CreateUser(ctx, user.ID, user.Username, user.Email); err != nil {
			log.Printf("user-service sync failed for oauth user %s: %v", user.ID, err)
		}
	}
	return user, nil
}

// linkAccount 把第三方身份绑定到指定用户。若该身份已绑定到别人 → ErrOAuthAlreadyLinked。
func (s *OAuthService) linkAccount(ctx context.Context, userID string, info *ProviderUserInfo) error {
	acc, err := s.oauthRepo.FindByProvider(ctx, info.Provider, info.ProviderID)
	if err != nil {
		return err
	}
	if acc != nil {
		if acc.UserID == userID {
			return nil // 已绑定到自己，幂等
		}
		return ErrOAuthAlreadyLinked
	}
	return s.oauthRepo.Create(ctx, &model.OAuthAccount{
		UserID: userID, Provider: info.Provider, ProviderUserID: info.ProviderID,
	})
}

func (s *OAuthService) Unlink(ctx context.Context, userID, provider string) error {
	return s.oauthRepo.DeleteByUserAndProvider(ctx, userID, provider)
}

func (s *OAuthService) ListLinked(ctx context.Context, userID string) ([]model.OAuthAccount, error) {
	return s.oauthRepo.ListByUser(ctx, userID)
}

// --- helpers ---

var usernameSanitizer = regexp.MustCompile(`[^a-zA-Z0-9_]`)

// generateUniqueUsername 基于 provider 昵称生成唯一用户名，冲突时加随机后缀重试。
// name 为空时回退到 provider 名（"google"/"phone"）。
func generateUniqueUsername(ctx context.Context, repo repository.UserRepository, name, provider string) (string, error) {
	base := sanitizeUsername(name)
	if base == "" {
		base = provider // "google" / "phone"
	}
	for attempt := 0; attempt < 4; attempt++ {
		var candidate string
		if attempt == 0 {
			candidate = truncate(base, 50)
		} else {
			candidate = truncate(base, 43) + "_" + randomSuffix(6)
		}
		u, err := repo.FindByUsername(ctx, candidate)
		if err != nil {
			return "", err
		}
		if u == nil {
			return candidate, nil
		}
	}
	return "", fmt.Errorf("could not allocate unique username for provider %s", provider)
}

func sanitizeUsername(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	s = usernameSanitizer.ReplaceAllString(s, "_")
	s = strings.Trim(s, "_")
	return s
}

func truncate(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n])
}

// randomToken 生成 32 字节 hex（64 字符）高熵 token，用作 state / 中间码。
func randomToken() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		// 极端情况；用时间兜底（不应发生）
		return fmt.Sprintf("%x", time.Now().UnixNano())
	}
	return hex.EncodeToString(b)
}

const base62Chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

func randomSuffix(n int) string {
	out := make([]rune, 0, n)
	for i := 0; i < n; i++ {
		idx, err := rand.Int(rand.Reader, big.NewInt(int64(len(base62Chars))))
		if err != nil {
			out = append(out, rune(base62Chars[0]))
			continue
		}
		out = append(out, rune(base62Chars[idx.Int64()]))
	}
	return string(out)
}
