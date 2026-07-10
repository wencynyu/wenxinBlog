package model

import "time"

type User struct {
	ID           string    `json:"id" db:"id"`
	Username     string    `json:"username" db:"username"`
	Email        string    `json:"email" db:"email"`
	PasswordHash string    `json:"-" db:"password_hash"`
	AvatarURL    string    `json:"avatarUrl,omitempty" db:"avatar_url"`
	Status       string    `json:"status" db:"status"` // ACTIVE, INACTIVE, BANNED
	TwoFAEnabled bool      `json:"twoFAEnabled" db:"two_fa_enabled"`
	TwoFASecret  string    `json:"-" db:"two_fa_secret"`
	CreatedAt    time.Time `json:"createdAt" db:"created_at"`
	UpdatedAt    time.Time `json:"updatedAt" db:"updated_at"`
}

type OAuthAccount struct {
	ID             string    `json:"id" db:"id"`
	UserID         string    `json:"userId" db:"user_id"`
	Provider       string    `json:"provider" db:"provider"`
	ProviderUserID string    `json:"providerUserId" db:"provider_user_id"`
	AccessToken    string    `json:"-" db:"access_token"`
	RefreshToken   string    `json:"-" db:"refresh_token"`
	ExpiresAt      time.Time `json:"expiresAt" db:"expires_at"`
	CreatedAt      time.Time `json:"createdAt" db:"created_at"`
}

type Session struct {
	ID        string    `json:"id" db:"id"`
	UserID    string    `json:"userId" db:"user_id"`
	TokenHash string    `json:"-" db:"token_hash"`
	IP        string    `json:"ip" db:"ip"`
	UserAgent string    `json:"userAgent" db:"user_agent"`
	ExpiresAt time.Time `json:"expiresAt" db:"expires_at"`
	CreatedAt time.Time `json:"createdAt" db:"created_at"`
}
