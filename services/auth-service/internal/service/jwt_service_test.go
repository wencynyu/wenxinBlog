package service

import (
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGenerateTokenPair_ValidTokens(t *testing.T) {
	service := NewJWTService("test-secret-key")
	userID := "user-123"
	roles := []string{"USER", "ADMIN"}

	tokens, err := service.GenerateTokenPair(userID, roles)
	require.NoError(t, err)
	assert.NotEmpty(t, tokens.AccessToken)
	assert.NotEmpty(t, tokens.RefreshToken)
	assert.Equal(t, int64(900), tokens.ExpiresIn) // 15 minutes in seconds
}

func TestGenerateTokenPair_AccessTokenExpiry(t *testing.T) {
	service := NewJWTService("test-secret-key")
	userID := "user-123"
	roles := []string{"USER"}

	tokens, err := service.GenerateTokenPair(userID, roles)
	require.NoError(t, err)

	// Parse access token to check expiry
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(tokens.AccessToken, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte("test-secret-key"), nil
	})
	require.NoError(t, err)
	require.True(t, token.Valid)

	// Check expiry is approximately 15 minutes from now
	expectedExpiry := time.Now().Add(15 * time.Minute)
	diff := expectedExpiry.Sub(claims.ExpiresAt.Time)
	assert.Less(t, diff.Abs(), 5*time.Second)
}

func TestGenerateTokenPair_RefreshTokenExpiry(t *testing.T) {
	service := NewJWTService("test-secret-key")
	userID := "user-123"
	roles := []string{"USER"}

	tokens, err := service.GenerateTokenPair(userID, roles)
	require.NoError(t, err)

	// Parse refresh token to check expiry
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(tokens.RefreshToken, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte("test-secret-key"), nil
	})
	require.NoError(t, err)
	require.True(t, token.Valid)

	// Check expiry is approximately 7 days from now
	expectedExpiry := time.Now().Add(7 * 24 * time.Hour)
	diff := expectedExpiry.Sub(claims.ExpiresAt.Time)
	assert.Less(t, diff.Abs(), 5*time.Second)
}

func TestGenerateTokenPair_ContainsCorrectClaims(t *testing.T) {
	service := NewJWTService("test-secret-key")
	userID := "user-456"
	roles := []string{"USER", "MODERATOR"}

	tokens, err := service.GenerateTokenPair(userID, roles)
	require.NoError(t, err)

	claims := &Claims{}
	token, err := jwt.ParseWithClaims(tokens.AccessToken, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte("test-secret-key"), nil
	})
	require.NoError(t, err)
	require.True(t, token.Valid)

	assert.Equal(t, userID, claims.UserID)
	assert.Equal(t, roles, claims.Roles)
	assert.Equal(t, userID, claims.Subject)
}

func TestParseToken_Valid(t *testing.T) {
	service := NewJWTService("test-secret-key")
	userID := "user-789"
	roles := []string{"USER"}

	tokens, err := service.GenerateTokenPair(userID, roles)
	require.NoError(t, err)

	claims, err := service.ParseToken(tokens.AccessToken)
	require.NoError(t, err)
	assert.Equal(t, userID, claims.UserID)
	assert.Equal(t, roles, claims.Roles)
}

func TestParseToken_Expired(t *testing.T) {
	service := NewJWTService("test-secret-key")

	// Create an expired token
	claims := Claims{
		UserID: "user-expired",
		Roles:  []string{"USER"},
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(-1 * time.Hour)),
			IssuedAt:  jwt.NewNumericDate(time.Now().Add(-2 * time.Hour)),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString([]byte("test-secret-key"))
	require.NoError(t, err)

	_, err = service.ParseToken(tokenString)
	assert.Error(t, err)
}

func TestParseToken_WrongSigningMethod(t *testing.T) {
	service := NewJWTService("test-secret-key")

	// Create a token with "none" signing method — ParseToken requires HMAC
	claims := Claims{
		UserID: "user-wrong-method",
		Roles:  []string{"USER"},
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(1 * time.Hour)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodNone, claims)
	tokenString, err := token.SignedString(jwt.UnsafeAllowNoneSignatureType)
	require.NoError(t, err)

	_, err = service.ParseToken(tokenString)
	assert.Error(t, err)
}

func TestParseToken_InvalidString(t *testing.T) {
	service := NewJWTService("test-secret-key")

	_, err := service.ParseToken("not-a-valid-token")
	assert.Error(t, err)
}

func TestParseToken_DifferentSecret(t *testing.T) {
	service1 := NewJWTService("secret-one")
	service2 := NewJWTService("secret-two")

	tokens, err := service1.GenerateTokenPair("user-123", []string{"USER"})
	require.NoError(t, err)

	_, err = service2.ParseToken(tokens.AccessToken)
	assert.Error(t, err)
}
