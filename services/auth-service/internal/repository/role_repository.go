package repository

import (
	"context"
	"database/sql"
	"errors"

	"wenxinblog/auth-service/internal/model"

	"github.com/lib/pq"
)

// 角色/权限管理错误。
var (
	ErrPermissionNotFound = errors.New("permission not found")
	ErrPermissionExists   = errors.New("permission code already exists")
	ErrRoleNotFound       = errors.New("role not found")
	ErrRoleExists         = errors.New("role code already exists")
	ErrRoleIsSystem       = errors.New("role is system-protected or not found")
	ErrRoleSelfParent     = errors.New("role cannot be its own parent")
)

// RoleRepository 角色/权限查询与分配。
type RoleRepository interface {
	// GetRolesForUser 返回用户完整角色链（含父角色继承），去重、按 level 升序。
	GetRolesForUser(ctx context.Context, userID string) ([]model.Role, error)
	// GetPermissionsForUser 沿角色链聚合去重后的权限（完整实体）。
	GetPermissionsForUser(ctx context.Context, userID string) ([]model.Permission, error)
	// AssignRole 幂等分配角色（不存在则 no-op）。
	AssignRole(ctx context.Context, userID, roleCode string) error
	// FindRoleByCode 按 code 查角色；不存在返回 (nil, nil)。
	FindRoleByCode(ctx context.Context, code string) (*model.Role, error)
	// GetRoleByID 按 id 查角色；不存在返回 (nil, nil)。
	GetRoleByID(ctx context.Context, id int64) (*model.Role, error)

	// 权限注册表管理（运行时 CRUD；code 唯一）。
	GetAllPermissions(ctx context.Context) ([]model.Permission, error)
	CreatePermission(ctx context.Context, perm *model.Permission) error
	DeletePermission(ctx context.Context, code string) error

	// 角色管理（非系统角色可删；级联由 FK ON DELETE CASCADE 处理）。
	GetAllRoles(ctx context.Context) ([]model.Role, error)
	CreateRole(ctx context.Context, code, name, description, parentCode string) (int64, error)
	DeleteRole(ctx context.Context, id int64) error

	// 角色↔权限动态配置。
	GetPermissionsForRole(ctx context.Context, roleID int64) ([]model.Permission, error)
	GrantPermissionToRole(ctx context.Context, roleID int64, permCode string) error
	RevokePermissionFromRole(ctx context.Context, roleID int64, permCode string) error
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
SELECT DISTINCT p.id, p.code, p.name, p.resource, p.action, p.scope, p.description
FROM role_chain rc
JOIN role_permissions rp ON rp.role_id = rc.id
JOIN permissions p ON p.id = rp.permission_id
ORDER BY p.code`

const assignRoleSQL = `
INSERT INTO user_roles (user_id, role_id)
SELECT $1, id FROM roles WHERE code = $2
ON CONFLICT (user_id, role_id) DO NOTHING`

const findRoleByCodeSQL = `SELECT id, code, name, description, parent_id, level, is_system FROM roles WHERE code = $1`

const findRoleByIDSQL = `SELECT id, code, name, description, parent_id, level, is_system FROM roles WHERE id = $1`

const allPermissionsSQL = `SELECT id, code, name, resource, action, scope, description FROM permissions ORDER BY code`

const createPermissionSQL = `
INSERT INTO permissions (code, name, resource, action, scope, description)
VALUES ($1, $2, $3, $4, $5, $6) RETURNING id`

const deletePermissionSQL = `DELETE FROM permissions WHERE code = $1`

const allRolesSQL = `SELECT id, code, name, description, parent_id, level, is_system FROM roles ORDER BY level, id`

const createRoleSQL = `
INSERT INTO roles (code, name, description, parent_id, level, is_system)
VALUES ($1, $2, $3, $4, $5, FALSE) RETURNING id`

const deleteRoleSQL = `DELETE FROM roles WHERE id = $1 AND is_system = FALSE`

const permsForRoleSQL = `
SELECT p.id, p.code, p.name, p.resource, p.action, p.scope, p.description
FROM role_permissions rp
JOIN permissions p ON p.id = rp.permission_id
WHERE rp.role_id = $1
ORDER BY p.code`

const grantPermToRoleSQL = `
INSERT INTO role_permissions (role_id, permission_id) VALUES ($1, $2)
ON CONFLICT (role_id, permission_id) DO NOTHING`

const revokePermFromRoleSQL = `DELETE FROM role_permissions WHERE role_id = $1 AND permission_id = $2`

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

func (r *RoleRepo) GetPermissionsForUser(ctx context.Context, userID string) ([]model.Permission, error) {
	rows, err := r.db.QueryContext(ctx, permsForUserSQL, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	perms := []model.Permission{}
	for rows.Next() {
		p, err := scanPermission(rows)
		if err != nil {
			return nil, err
		}
		perms = append(perms, p)
	}
	return perms, rows.Err()
}

func (r *RoleRepo) AssignRole(ctx context.Context, userID, roleCode string) error {
	_, err := r.db.ExecContext(ctx, assignRoleSQL, userID, roleCode)
	return err
}

func (r *RoleRepo) FindRoleByCode(ctx context.Context, code string) (*model.Role, error) {
	role, err := scanRoleFull(r.db.QueryRowContext(ctx, findRoleByCodeSQL, code))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &role, nil
}

func (r *RoleRepo) GetRoleByID(ctx context.Context, id int64) (*model.Role, error) {
	role, err := scanRoleFull(r.db.QueryRowContext(ctx, findRoleByIDSQL, id))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &role, nil
}

func (r *RoleRepo) GetAllPermissions(ctx context.Context) ([]model.Permission, error) {
	rows, err := r.db.QueryContext(ctx, allPermissionsSQL)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	perms := []model.Permission{}
	for rows.Next() {
		p, err := scanPermission(rows)
		if err != nil {
			return nil, err
		}
		perms = append(perms, p)
	}
	return perms, rows.Err()
}

func (r *RoleRepo) CreatePermission(ctx context.Context, perm *model.Permission) error {
	var scope interface{}
	if perm.Scope != nil {
		scope = *perm.Scope
	}
	err := r.db.QueryRowContext(ctx, createPermissionSQL,
		perm.Code, perm.Name, perm.Resource, perm.Action, scope, perm.Description).Scan(&perm.ID)
	if err != nil {
		if pqErr, ok := err.(*pq.Error); ok && pqErr.Code == "23505" {
			return ErrPermissionExists
		}
		return err
	}
	return nil
}

func (r *RoleRepo) DeletePermission(ctx context.Context, code string) error {
	_, err := r.db.ExecContext(ctx, deletePermissionSQL, code)
	return err
}

func (r *RoleRepo) GetAllRoles(ctx context.Context) ([]model.Role, error) {
	rows, err := r.db.QueryContext(ctx, allRolesSQL)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	roles := []model.Role{}
	for rows.Next() {
		role, err := scanRoleFull(rows)
		if err != nil {
			return nil, err
		}
		roles = append(roles, role)
	}
	return roles, rows.Err()
}

func (r *RoleRepo) CreateRole(ctx context.Context, code, name, description, parentCode string) (int64, error) {
	var parentID sql.NullInt64
	level := 0
	if parentCode != "" {
		if parentCode == code {
			return 0, ErrRoleSelfParent
		}
		var parentLevel int
		err := r.db.QueryRowContext(ctx, `SELECT id, level FROM roles WHERE code = $1`, parentCode).
			Scan(&parentID, &parentLevel)
		if errors.Is(err, sql.ErrNoRows) {
			return 0, ErrRoleNotFound
		}
		if err != nil {
			return 0, err
		}
		level = parentLevel + 1
	}
	var id int64
	err := r.db.QueryRowContext(ctx, createRoleSQL, code, name, description, parentID, level).Scan(&id)
	if err != nil {
		if pqErr, ok := err.(*pq.Error); ok && pqErr.Code == "23505" {
			return 0, ErrRoleExists
		}
		return 0, err
	}
	return id, nil
}

func (r *RoleRepo) DeleteRole(ctx context.Context, id int64) error {
	res, err := r.db.ExecContext(ctx, deleteRoleSQL, id)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return ErrRoleIsSystem
	}
	return nil
}

func (r *RoleRepo) GetPermissionsForRole(ctx context.Context, roleID int64) ([]model.Permission, error) {
	rows, err := r.db.QueryContext(ctx, permsForRoleSQL, roleID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	perms := []model.Permission{}
	for rows.Next() {
		p, err := scanPermission(rows)
		if err != nil {
			return nil, err
		}
		perms = append(perms, p)
	}
	return perms, rows.Err()
}

func (r *RoleRepo) GrantPermissionToRole(ctx context.Context, roleID int64, permCode string) error {
	permID, err := r.permissionIDByCode(ctx, permCode)
	if err != nil {
		return err
	}
	_, err = r.db.ExecContext(ctx, grantPermToRoleSQL, roleID, permID)
	return err
}

func (r *RoleRepo) RevokePermissionFromRole(ctx context.Context, roleID int64, permCode string) error {
	permID, err := r.permissionIDByCode(ctx, permCode)
	if err != nil {
		return err
	}
	_, err = r.db.ExecContext(ctx, revokePermFromRoleSQL, roleID, permID)
	return err
}

// permissionIDByCode 按 code 查权限 id；不存在返回 ErrPermissionNotFound。
func (r *RoleRepo) permissionIDByCode(ctx context.Context, code string) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, `SELECT id FROM permissions WHERE code = $1`, code).Scan(&id)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, ErrPermissionNotFound
	}
	return id, err
}

// scanPermission 从一行扫描出权限实体（scope/description 可空 → 安全处理）。
func scanPermission(rows *sql.Rows) (model.Permission, error) {
	var p model.Permission
	var scope, description sql.NullString
	if err := rows.Scan(&p.ID, &p.Code, &p.Name, &p.Resource, &p.Action, &scope, &description); err != nil {
		return p, err
	}
	if scope.Valid {
		s := scope.String
		p.Scope = &s
	}
	if description.Valid {
		p.Description = description.String
	}
	return p, nil
}

// rowScanner 兼容 *sql.Rows 与 *sql.Row 的 Scan。
type rowScanner interface {
	Scan(dest ...interface{}) error
}

// scanRoleFull 扫描完整角色行（parent_id/description 可空 → 安全处理）。
func scanRoleFull(s rowScanner) (model.Role, error) {
	var role model.Role
	var parentID sql.NullInt64
	var description sql.NullString
	if err := s.Scan(&role.ID, &role.Code, &role.Name, &description, &parentID, &role.Level, &role.IsSystem); err != nil {
		return role, err
	}
	if parentID.Valid {
		role.ParentID = &parentID.Int64
	}
	if description.Valid {
		role.Description = description.String
	}
	return role, nil
}
