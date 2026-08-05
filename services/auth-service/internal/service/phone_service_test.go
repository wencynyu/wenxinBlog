package service

import (
	"context"
	"testing"

	"wenxinblog/auth-service/internal/cache"
	"wenxinblog/auth-service/internal/model"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// captureSender 记录最近一次发送的验证码（mock SmsSender）。
type captureSender struct{ code, phone string }

func (c *captureSender) Send(_ context.Context, phone, code string) error {
	c.phone, c.code = phone, code
	return nil
}

func newMiniredisCache(t *testing.T) (*cache.Cache, func()) {
	t.Helper()
	mr := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: mr.Addr()})
	return cache.New(rdb), func() { _ = rdb.Close() }
}

func TestPhoneService_SendCode_RateLimit(t *testing.T) {
	c, cleanup := newMiniredisCache(t)
	defer cleanup()
	auth := NewAuthService(&MockUserRepository{}, &MockRoleRepository{}, NewJWTService("test-secret"))
	svc := NewPhoneService(auth, c, &captureSender{})

	phone := "13800138000"
	require.NoError(t, svc.SendCode(context.Background(), phone, "10.0.0.1"))
	// 同一号码 1 分钟内第二次 → 限流
	err := svc.SendCode(context.Background(), phone, "10.0.0.1")
	assert.ErrorIs(t, err, ErrRateLimited)
}

func TestPhoneService_SendCode_InvalidPhone(t *testing.T) {
	c, cleanup := newMiniredisCache(t)
	defer cleanup()
	svc := NewPhoneService(NewAuthService(&MockUserRepository{}, &MockRoleRepository{}, NewJWTService("s")), c, &captureSender{})
	assert.ErrorIs(t, svc.SendCode(context.Background(), "123", "1.2.3.4"), ErrInvalidPhone)
}

func TestPhoneService_Login_WrongCode(t *testing.T) {
	c, cleanup := newMiniredisCache(t)
	defer cleanup()
	svc := NewPhoneService(NewAuthService(&MockUserRepository{}, &MockRoleRepository{}, NewJWTService("s")), c, &captureSender{})
	_, _, err := svc.Login(context.Background(), "13800138000", "000000")
	assert.ErrorIs(t, err, ErrInvalidCredentials)
}

func TestPhoneService_Login_CreatesNewUser(t *testing.T) {
	c, cleanup := newMiniredisCache(t)
	defer cleanup()
	sender := &captureSender{}

	var created model.User
	repo := &MockUserRepository{
		FindByPhoneFunc:    func(context.Context, string) (*model.User, error) { return nil, nil },
		FindByUsernameFunc: func(context.Context, string) (*model.User, error) { return nil, nil },
		CreateNoPasswordFunc: func(_ context.Context, u *model.User) error {
			u.ID = "new-phone-user"
			u.Status = "ACTIVE" // 模拟真实 repo 的默认行为
			created = *u
			return nil
		},
	}
	auth := NewAuthService(repo, &MockRoleRepository{}, NewJWTService("test-secret"))
	svc := NewPhoneService(auth, c, sender)

	require.NoError(t, svc.SendCode(context.Background(), "13800138000", "10.0.0.1"))

	tokens, user, err := svc.Login(context.Background(), "13800138000", sender.code)
	require.NoError(t, err)
	require.NotNil(t, tokens)
	assert.NotEmpty(t, tokens.AccessToken)
	assert.Equal(t, "new-phone-user", user.ID)
	assert.Equal(t, "13800138000", created.Phone, "new user should be created with the phone")
}
