package model

import "time"

// AdminUser 用户管理列表/详情视图（不含密码等敏感字段，附加角色 code）。
type AdminUser struct {
	ID        string    `json:"id"`
	Username  string    `json:"username"`
	Email     string    `json:"email"`
	AvatarURL string    `json:"avatarUrl,omitempty"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"createdAt"`
	Roles     []string  `json:"roles"`
}
