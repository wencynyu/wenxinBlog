package repository

import (
	"context"
	"database/sql"
	"errors"

	"wenxinblog/auth-service/internal/model"
)

// RoleRepository 角色/权限查询与分配。
type RoleRepository interface {
	// GetRolesForUser 返回用户完整角色链（含父角色继承），去重、按 level 升序。
	GetRolesForUser(ctx context.Context, userID string) ([]model.Role, error)
	// GetPermissionsForUser 沿角色链聚合去重后的权限 code 列表。
	GetPermissionsForUser(ctx context.Context, userID string) ([]string, error)
	// AssignRole 幂等分配角色（不存在则 no-op）。
	AssignRole(ctx context.Context, userID, roleCode string) error
	// FindRoleByCode 按 code 查角色；不存在返回 (nil, nil)。
	FindRoleByCode(ctx context.Context, code string) (*model.Role, error)
}

type RoleRepo struct {
	db *sql.DB
}

var _ RoleRepository = (*RoleRepo)(nil)

func NewRoleRepo(db *sql.DB) *RoleRepo {
	return &RoleRepo{db: db}
}

const rolesForUserSQL = `
WITH RECURSIVE role_chain AS (
    SELECT r.id, r.parent_id, r.level, r.code, r.name, r.is_system
    FROM roles r
    JOIN user_roles ur ON ur.role_id = r.id
    WHERE ur.user_id = $1
      AND (ur.expires_at IS NULL OR ur.expires_at > now())
    UNION
    SELECT r.id, r.parent_id, r.level, r.code, r.name, r.is_system
    FROM roles r
    JOIN role_chain rc ON rc.parent_id = r.id
)
SELECT DISTINCT id, code, name, level, is_system FROM role_chain ORDER BY level`

const permsForUserSQL = `
WITH RECURSIVE role_chain AS (
    SELECT r.id, r.parent_id
    FROM roles r
    JOIN user_roles ur ON ur.role_id = r.id
    WHERE ur.user_id = $1
      AND (ur.expires_at IS NULL OR ur.expires_at > now())
    UNION
    SELECT r.id, r.parent_id
    FROM roles r
    JOIN role_chain rc ON rc.parent_id = r.id
)
SELECT DISTINCT p.code
FROM role_chain rc
JOIN role_permissions rp ON rp.role_id = rc.id
JOIN permissions p ON p.id = rp.permission_id
ORDER BY p.code`

const assignRoleSQL = `
INSERT INTO user_roles (user_id, role_id)
SELECT $1, id FROM roles WHERE code = $2
ON CONFLICT (user_id, role_id) DO NOTHING`

const findRoleByCodeSQL = `SELECT id, code, name, description, parent_id, level, is_system FROM roles WHERE code = $1`

func (r *RoleRepo) GetRolesForUser(ctx context.Context, userID string) ([]model.Role, error) {
	rows, err := r.db.QueryContext(ctx, rolesForUserSQL, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	roles := []model.Role{}
	for rows.Next() {
		var role model.Role
		if err := rows.Scan(&role.ID, &role.Code, &role.Name, &role.Level, &role.IsSystem); err != nil {
			return nil, err
		}
		roles = append(roles, role)
	}
	return roles, rows.Err()
}

func (r *RoleRepo) GetPermissionsForUser(ctx context.Context, userID string) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, permsForUserSQL, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	perms := []string{}
	for rows.Next() {
		var code string
		if err := rows.Scan(&code); err != nil {
			return nil, err
		}
		perms = append(perms, code)
	}
	return perms, rows.Err()
}

func (r *RoleRepo) AssignRole(ctx context.Context, userID, roleCode string) error {
	_, err := r.db.ExecContext(ctx, assignRoleSQL, userID, roleCode)
	return err
}

func (r *RoleRepo) FindRoleByCode(ctx context.Context, code string) (*model.Role, error) {
	row := r.db.QueryRowContext(ctx, findRoleByCodeSQL, code)
	var role model.Role
	if err := row.Scan(&role.ID, &role.Code, &role.Name, &role.Description, &role.ParentID, &role.Level, &role.IsSystem); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &role, nil
}
