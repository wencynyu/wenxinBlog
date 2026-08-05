package service

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestGoogleProvider_Exchange 验证 Google code→token→userinfo 流程与字段映射。
func TestGoogleProvider_Exchange(t *testing.T) {
	tokenSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{"access_token": "g-token"})
	}))
	defer tokenSrv.Close()

	infoSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "Bearer g-token", r.Header.Get("Authorization"))
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"sub": "g-sub-123", "email": "u@example.com", "name": "Alice",
			"picture": "https://img/avatar.png", "email_verified": true,
		})
	}))
	defer infoSrv.Close()

	p := NewGoogleProvider("cid", "secret", "https://app/cb")
	p.tokenURL = tokenSrv.URL
	p.userInfoURL = infoSrv.URL

	info, err := p.Exchange(context.Background(), "the-code")
	require.NoError(t, err)
	assert.Equal(t, "google", info.Provider)
	assert.Equal(t, "g-sub-123", info.ProviderID)
	assert.Equal(t, "u@example.com", info.Email)
	assert.Equal(t, "Alice", info.Name)
	assert.Equal(t, "https://img/avatar.png", info.AvatarURL)

	// AuthURL 应含 state 与必要参数
	u := p.AuthURL("st8")
	assert.True(t, strings.Contains(u, "state=st8"))
	assert.True(t, strings.Contains(u, "client_id=cid"))
}

// TestGoogleProvider_UnverifiedEmailDropped 未验证邮箱不作为标识（清空）。
func TestGoogleProvider_UnverifiedEmailDropped(t *testing.T) {
	tokenSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"access_token": "t"})
	}))
	defer tokenSrv.Close()
	infoSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"sub": "s", "email": "x@y.com", "email_verified": false})
	}))
	defer infoSrv.Close()

	p := NewGoogleProvider("cid", "secret", "cb")
	p.tokenURL, p.userInfoURL = tokenSrv.URL, infoSrv.URL
	info, err := p.Exchange(context.Background(), "c")
	require.NoError(t, err)
	assert.Equal(t, "s", info.ProviderID)
	assert.Empty(t, info.Email, "unverified email must be dropped")
}
