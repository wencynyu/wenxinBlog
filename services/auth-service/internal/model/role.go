package model

// Role 角色（支持 parent_id 继承 + level）。
type Role struct {
	ID          int64  `json:"id" db:"id"`
	Code        string `json:"code" db:"code"`
	Name        string `json:"name" db:"name"`
	Description string `json:"description" db:"description"`
	ParentID    *int64 `json:"parentId" db:"parent_id"`
	Level       int    `json:"level" db:"level"`
	IsSystem    bool   `json:"isSystem" db:"is_system"`
}
