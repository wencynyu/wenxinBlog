package service

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// UserSyncClient 把 auth 注册的用户同步到 user-service（跨库，幂等）。
type UserSyncClient interface {
	CreateUser(ctx context.Context, id, username, email string) error
}

// HTTPUserSyncClient 通过 HTTP 调用 user-service 的内部接口 POST /internal/users。
type HTTPUserSyncClient struct {
	baseURL string
	client  *http.Client
}

func NewHTTPUserSyncClient(baseURL string) *HTTPUserSyncClient {
	return &HTTPUserSyncClient{
		baseURL: strings.TrimRight(baseURL, "/"),
		client:  &http.Client{Timeout: 5 * time.Second},
	}
}

func (c *HTTPUserSyncClient) CreateUser(ctx context.Context, id, username, email string) error {
	body, err := json.Marshal(map[string]string{
		"id":       id,
		"username": username,
		"email":    email,
	})
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/internal/users", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("user-service returned status %d", resp.StatusCode)
	}
	return nil
}
