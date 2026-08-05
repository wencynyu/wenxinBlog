package model

// Permission 权限（code = resource:action[:scope]）。与 DB permissions 表对齐。
// 运行时授权只消费 Code；其余字段供管理端点展示与角色权限配置。
type Permission struct {
	ID          int64   `json:"id" db:"id"`
	Code        string  `json:"code" db:"code"`
	Name        string  `json:"name" db:"name"`
	Resource    string  `json:"resource" db:"resource"`
	Action      string  `json:"action" db:"action"`
	Scope       *string `json:"scope" db:"scope"`
	Description string  `json:"description" db:"description"`
}
