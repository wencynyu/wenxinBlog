package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"log"
	"math/big"
	"regexp"
	"time"

	"wenxinblog/auth-service/internal/cache"
	"wenxinblog/auth-service/internal/model"
)

var (
	ErrInvalidPhone = errors.New("invalid phone number")
	ErrRateLimited  = errors.New("rate limited")
)

var phoneRegex = regexp.MustCompile(`^1[3-9]\d{9}$`)

const (
	smsCodeTTL     = 5 * time.Minute
	smsCodeLen     = 6
	phonePerMinute = 1
	phonePerDay    = 5
	ipPerHour      = 10
)

type PhoneService struct {
	auth   *AuthService
	cache  *cache.Cache
	sender SmsSender
}

func NewPhoneService(auth *AuthService, cache *cache.Cache, sender SmsSender) *PhoneService {
	return &PhoneService{auth: auth, cache: cache, sender: sender}
}

// SendCode 限流后生成 6 位验证码：哈希后存 Redis（5m），明文经 SmsSender 发送。
// clientIP 用于按 IP 限流（防刷），由 handler 传入。
func (s *PhoneService) SendCode(ctx context.Context, phone, clientIP string) error {
	if !phoneRegex.MatchString(phone) {
		return ErrInvalidPhone
	}
	if n, _ := s.cache.IncrRate(ctx, "rl:sms:phone:min:"+phone, time.Minute); n > phonePerMinute {
		return ErrRateLimited
	}
	if n, _ := s.cache.IncrRate(ctx, "rl:sms:phone:"+phone, 24*time.Hour); n > phonePerDay {
		return ErrRateLimited
	}
	if clientIP != "" {
		if n, _ := s.cache.IncrRate(ctx, "rl:sms:ip:"+clientIP, time.Hour); n > ipPerHour {
			return ErrRateLimited
		}
	}

	code := randomCode6()
	if err := s.cache.SaveSMSCode(ctx, phone, hashCode(code), smsCodeTTL); err != nil {
		return err
	}
	return s.sender.Send(ctx, phone, code)
}

// Login 校验验证码（一次性消费）→ 已注册则登录，未注册则创建纯手机号用户 → 签发 token。
func (s *PhoneService) Login(ctx context.Context, phone, code string) (*TokenPair, *model.User, error) {
	stored, err := s.cache.ConsumeSMSCode(ctx, phone)
	// 统一返回 ErrInvalidCredentials（不区分「无验证码/验证码错误」，避免枚举）
	if err != nil || stored == "" || hashCode(code) != stored {
		return nil, nil, ErrInvalidCredentials
	}

	user, err := s.auth.userRepo.FindByPhone(ctx, phone)
	if err != nil {
		return nil, nil, err
	}
	if user == nil {
		username, err := generateUniqueUsername(ctx, s.auth.userRepo, "", "phone")
		if err != nil {
			return nil, nil, err
		}
		user = &model.User{Phone: phone, Username: username}
		if err := s.auth.userRepo.CreateNoPassword(ctx, user); err != nil {
			return nil, nil, err
		}
		if s.auth.roleRepo != nil {
			if err := s.auth.roleRepo.AssignRole(ctx, user.ID, "user"); err != nil {
				log.Printf("assign default role failed for phone user %s: %v", user.ID, err)
			}
		}
		if s.auth.userSync != nil {
			if err := s.auth.userSync.CreateUser(ctx, user.ID, user.Username, user.Email); err != nil {
				log.Printf("user-service sync failed for phone user %s: %v", user.ID, err)
			}
		}
	}
	if user.Status != "ACTIVE" {
		return nil, nil, ErrInvalidCredentials
	}
	tokens, err := s.auth.issueTokens(ctx, user.ID)
	if err != nil {
		return nil, nil, err
	}
	return tokens, user, nil
}

func randomCode6() string {
	n, err := rand.Int(rand.Reader, big.NewInt(1000000))
	if err != nil {
		return fmt.Sprintf("%06d", time.Now().UnixNano()%1000000)
	}
	return fmt.Sprintf("%06d", n.Int64())
}

func hashCode(code string) string {
	h := sha256.Sum256([]byte(code))
	return hex.EncodeToString(h[:])
}
