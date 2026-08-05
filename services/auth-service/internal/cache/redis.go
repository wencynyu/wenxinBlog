// Package cache 封装 Redis 客户端，供 OAuth（state/中间码）与手机号（验证码/限流）使用。
// 离线环境下依赖已在模块缓存中的 github.com/redis/go-redis/v9（与 user-service 同版本）。
package cache

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

type Cache struct {
	rdb redis.Cmdable
}

// New 包装一个已建好的 redis 客户端（或 miniredis 等实现 redis.Cmdable 的对象）。
func New(rdb redis.Cmdable) *Cache {
	return &Cache{rdb: rdb}
}

// NewClient 按 redis:// URL（或纯 host:port）连接并 Ping，供 main.go fail-fast 用。
func NewClient(url string) (*redis.Client, error) {
	opts, err := redis.ParseURL(url)
	if err != nil {
		opts = &redis.Options{Addr: url}
	}
	rdb := redis.NewClient(opts)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := rdb.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("redis ping: %w", err)
	}
	return rdb, nil
}

// --- OAuth state（login / link 意图，一次性） ---

// OAuthState 携带发起 OAuth 时的意图。login=登录；link:<userId>=绑定到已登录用户。
type OAuthState struct {
	Intent   string `json:"intent"`
	Provider string `json:"provider"`
}

func (c *Cache) SaveOAuthState(ctx context.Context, state string, s OAuthState, ttl time.Duration) error {
	b, err := json.Marshal(s)
	if err != nil {
		return err
	}
	return c.rdb.Set(ctx, "oauth:state:"+state, b, ttl).Err()
}

// ConsumeOAuthState 原子读取并删除（一次性，防重放）。不存在返回错误。
func (c *Cache) ConsumeOAuthState(ctx context.Context, state string) (*OAuthState, error) {
	b, err := c.rdb.GetDel(ctx, "oauth:state:"+state).Bytes()
	if err != nil {
		return nil, err
	}
	var s OAuthState
	if err := json.Unmarshal(b, &s); err != nil {
		return nil, err
	}
	return &s, nil
}

// --- OAuth 中间码（BFF 模式：后端换码后签发，前端用它换 JWT，避免 token 进 URL） ---

type IntermediateCode struct {
	UserID   string `json:"userId,omitempty"` // outcome=login 时有值
	Outcome  string `json:"outcome"`          // "login" | "link"
	Provider string `json:"provider,omitempty"`
}

func (c *Cache) SaveIntermediateCode(ctx context.Context, code string, ic IntermediateCode, ttl time.Duration) error {
	b, err := json.Marshal(ic)
	if err != nil {
		return err
	}
	return c.rdb.Set(ctx, "oauth:code:"+code, b, ttl).Err()
}

func (c *Cache) ConsumeIntermediateCode(ctx context.Context, code string) (*IntermediateCode, error) {
	b, err := c.rdb.GetDel(ctx, "oauth:code:"+code).Bytes()
	if err != nil {
		return nil, err
	}
	var ic IntermediateCode
	if err := json.Unmarshal(b, &ic); err != nil {
		return nil, err
	}
	return &ic, nil
}

// --- 手机号验证码 ---

// SaveSMSCode 存储验证码（建议存哈希，非明文）。
func (c *Cache) SaveSMSCode(ctx context.Context, phone, hashedCode string, ttl time.Duration) error {
	return c.rdb.Set(ctx, "sms:code:"+phone, hashedCode, ttl).Err()
}

// ConsumeSMSCode 原子读取并删除（验证即作废）。不存在返回错误。
func (c *Cache) ConsumeSMSCode(ctx context.Context, phone string) (string, error) {
	b, err := c.rdb.GetDel(ctx, "sms:code:"+phone).Bytes()
	if err != nil {
		return "", err
	}
	return string(b), nil
}

// --- 限流（固定窗口：INCR + 首次 EXPIRE） ---

// IncrRate 自增计数器，首次自增时设置窗口 ttl。返回窗口内当前计数。
func (c *Cache) IncrRate(ctx context.Context, key string, ttl time.Duration) (int64, error) {
	n, err := c.rdb.Incr(ctx, key).Result()
	if err != nil {
		return 0, err
	}
	if n == 1 {
		// 忽略 Expire 错误：即使没设上，计数器也会随下次窗口重置逻辑受影响，不致命。
		_ = c.rdb.Expire(ctx, key, ttl).Err()
	}
	return n, nil
}
