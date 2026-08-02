package repository

import (
	"context"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRoleRepo_GetPermissionsForUser(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()
	repo := NewRoleRepo(db)

	mock.ExpectQuery("SELECT DISTINCT p.code").
		WithArgs("user-123").
		WillReturnRows(sqlmock.NewRows([]string{"code"}).
			AddRow("post:create").AddRow("post:update:own"))

	perms, err := repo.GetPermissionsForUser(context.Background(), "user-123")
	require.NoError(t, err)
	assert.Equal(t, []string{"post:create", "post:update:own"}, perms)
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
