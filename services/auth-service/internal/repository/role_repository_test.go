package repository

import (
	"context"
	"testing"

	"wenxinblog/auth-service/internal/model"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/lib/pq"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRoleRepo_GetPermissionsForUser(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT DISTINCT p.id").
		WithArgs("user-123").
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "resource", "action", "scope", "description"}).
			AddRow(1, "post:create", "创建博文", "post", "create", nil, "创建新博文").
			AddRow(5, "post:update:own", "更新自己的博文", "post", "update", "own", "更新自己创建的博文"))

	perms, err := repo.GetPermissionsForUser(context.Background(), "user-123")
	require.NoError(t, err)
	require.Len(t, perms, 2)
	assert.Equal(t, "post:create", perms[0].Code)
	assert.Nil(t, perms[0].Scope) // NULL scope → nil
	assert.Equal(t, "post:update:own", perms[1].Code)
	assert.Equal(t, "post", perms[1].Resource)
	require.NotNil(t, perms[1].Scope)
	assert.Equal(t, "own", *perms[1].Scope)
	require.NoError(t, mock.ExpectationsWereMet())
}

// 回归测试：存量 DB 里 description=NULL 的权限不能让 scan 报错，
// 否则 resolveRolesAndPermissions 失败 → Login fallback 空 permissions → 无法发博文。
func TestRoleRepo_GetPermissionsForUser_NullDescription(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT DISTINCT p.id").
		WithArgs("user-1").
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "resource", "action", "scope", "description"}).
			AddRow(2, "post:create", "创建博文", "post", "create", nil, nil))

	perms, err := repo.GetPermissionsForUser(context.Background(), "user-1")
	require.NoError(t, err)
	require.Len(t, perms, 1)
	assert.Equal(t, "post:create", perms[0].Code)
	assert.Equal(t, "", perms[0].Description) // NULL → 空串，不报错
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GetRolesForUser(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT DISTINCT id, code, name, level, is_system").
		WithArgs("user-123").
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "level", "is_system"}).
			AddRow(1, "guest", "访客", 0, true).
			AddRow(2, "user", "普通用户", 1, true))

	roles, err := repo.GetRolesForUser(context.Background(), "user-123")
	require.NoError(t, err)
	require.Len(t, roles, 2)
	assert.Equal(t, "guest", roles[0].Code)
	assert.Equal(t, "user", roles[1].Code)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_AssignRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectExec("INSERT INTO user_roles").
		WithArgs("user-123", "admin").
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.AssignRole(context.Background(), "user-123", "admin")
	require.NoError(t, err)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_FindRoleByCode_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id, code, name").
		WithArgs("nope").
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "description", "parent_id", "level", "is_system"}))

	role, err := repo.FindRoleByCode(context.Background(), "nope")
	require.NoError(t, err)
	assert.Nil(t, role)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GetAllPermissions(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id, code, name, resource, action, scope, description FROM permissions").
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "resource", "action", "scope", "description"}).
			AddRow(1, "post:read", "查看博文", "post", "read", nil, "").
			AddRow(22, "role:manage", "管理角色与权限", "role", "manage", nil, ""))

	perms, err := repo.GetAllPermissions(context.Background())
	require.NoError(t, err)
	require.Len(t, perms, 2)
	assert.Equal(t, "post:read", perms[0].Code)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_CreatePermission(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("INSERT INTO permissions").
		WithArgs("custom:demo", "演示", "custom", "demo", nil, "desc").
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(23))

	perm := &model.Permission{Code: "custom:demo", Name: "演示", Resource: "custom", Action: "demo", Description: "desc"}
	err = repo.CreatePermission(context.Background(), perm)
	require.NoError(t, err)
	assert.Equal(t, int64(23), perm.ID)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_CreatePermission_Duplicate(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("INSERT INTO permissions").
		WillReturnError(&pq.Error{Code: "23505"})

	err = repo.CreatePermission(context.Background(), &model.Permission{Code: "post:read", Name: "x", Resource: "post", Action: "read"})
	assert.ErrorIs(t, err, ErrPermissionExists)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_DeletePermission(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectExec("DELETE FROM permissions WHERE code").
		WithArgs("custom:demo").
		WillReturnResult(sqlmock.NewResult(0, 1))

	require.NoError(t, repo.DeletePermission(context.Background(), "custom:demo"))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GetAllRoles(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	// description/parent_id 均可能为 NULL（如 guest），scan 必须安全。
	mock.ExpectQuery("SELECT id, code, name, description, parent_id, level, is_system FROM roles").
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "description", "parent_id", "level", "is_system"}).
			AddRow(1, "guest", "访客", nil, nil, 0, true).
			AddRow(2, "user", "普通用户", nil, 1, 1, true))

	roles, err := repo.GetAllRoles(context.Background())
	require.NoError(t, err)
	require.Len(t, roles, 2)
	assert.Nil(t, roles[0].ParentID)
	assert.Equal(t, "", roles[0].Description) // NULL → 空串
	require.NotNil(t, roles[1].ParentID)
	assert.Equal(t, int64(1), *roles[1].ParentID)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GetRoleByID(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id, code, name, description, parent_id, level, is_system FROM roles WHERE id").
		WithArgs(3).
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "description", "parent_id", "level", "is_system"}).
			AddRow(3, "author", "作者", "", 2, 2, true))

	role, err := repo.GetRoleByID(context.Background(), 3)
	require.NoError(t, err)
	require.NotNil(t, role)
	assert.Equal(t, "author", role.Code)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GetRoleByID_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id, code, name, description, parent_id, level, is_system FROM roles WHERE id").
		WithArgs(999).
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "description", "parent_id", "level", "is_system"}))

	role, err := repo.GetRoleByID(context.Background(), 999)
	require.NoError(t, err)
	assert.Nil(t, role)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_CreateRole_WithParent(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	// 查父级 id+level
	mock.ExpectQuery("SELECT id, level FROM roles WHERE code").
		WithArgs("user").
		WillReturnRows(sqlmock.NewRows([]string{"id", "level"}).AddRow(2, 1))
	// 插入（parent_id=2, level=2）
	mock.ExpectQuery("INSERT INTO roles").
		WithArgs("editor", "编辑", "", 2, 2).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(6))

	id, err := repo.CreateRole(context.Background(), "editor", "编辑", "", "user")
	require.NoError(t, err)
	assert.Equal(t, int64(6), id)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_CreateRole_NoParent(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("INSERT INTO roles").
		WithArgs("vip", "VIP", "", nil, 0).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(7))

	id, err := repo.CreateRole(context.Background(), "vip", "VIP", "", "")
	require.NoError(t, err)
	assert.Equal(t, int64(7), id)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_CreateRole_SelfParent(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	_, err = repo.CreateRole(context.Background(), "editor", "编辑", "", "editor")
	assert.ErrorIs(t, err, ErrRoleSelfParent)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_CreateRole_ParentNotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id, level FROM roles WHERE code").
		WithArgs("nope").
		WillReturnRows(sqlmock.NewRows([]string{"id", "level"}))

	_, err = repo.CreateRole(context.Background(), "editor", "编辑", "", "nope")
	assert.ErrorIs(t, err, ErrRoleNotFound)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_DeleteRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectExec("DELETE FROM roles WHERE id").
		WithArgs(6).
		WillReturnResult(sqlmock.NewResult(0, 1))

	require.NoError(t, repo.DeleteRole(context.Background(), 6))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_DeleteRole_SystemProtected(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	// is_system=TRUE → 0 行
	mock.ExpectExec("DELETE FROM roles WHERE id").
		WithArgs(5).
		WillReturnResult(sqlmock.NewResult(0, 0))

	err = repo.DeleteRole(context.Background(), 5)
	assert.ErrorIs(t, err, ErrRoleIsSystem)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GetPermissionsForRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT p.id, p.code").
		WithArgs(2).
		WillReturnRows(sqlmock.NewRows([]string{"id", "code", "name", "resource", "action", "scope", "description"}).
			AddRow(2, "post:create", "创建博文", "post", "create", nil, ""))

	perms, err := repo.GetPermissionsForRole(context.Background(), 2)
	require.NoError(t, err)
	require.Len(t, perms, 1)
	assert.Equal(t, "post:create", perms[0].Code)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GrantPermissionToRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id FROM permissions WHERE code").
		WithArgs("post:create").
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(2))
	mock.ExpectExec("INSERT INTO role_permissions").
		WithArgs(3, 2).
		WillReturnResult(sqlmock.NewResult(0, 1))

	require.NoError(t, repo.GrantPermissionToRole(context.Background(), 3, "post:create"))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_GrantPermissionToRole_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id FROM permissions WHERE code").
		WithArgs("nope").
		WillReturnRows(sqlmock.NewRows([]string{"id"}))

	err = repo.GrantPermissionToRole(context.Background(), 3, "nope")
	assert.ErrorIs(t, err, ErrPermissionNotFound)
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestRoleRepo_RevokePermissionFromRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT id FROM permissions WHERE code").
		WithArgs("post:create").
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(2))
	mock.ExpectExec("DELETE FROM role_permissions").
		WithArgs(3, 2).
		WillReturnResult(sqlmock.NewResult(0, 1))

	require.NoError(t, repo.RevokePermissionFromRole(context.Background(), 3, "post:create"))
	require.NoError(t, mock.ExpectationsWereMet())
}
