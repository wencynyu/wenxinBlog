package repository

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"wenxinblog/auth-service/internal/model"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestUserRepository_Create_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	mock.ExpectExec("INSERT INTO users").
		WithArgs(sqlmock.AnyArg(), "testuser", "test@example.com", sqlmock.AnyArg(), "ACTIVE", false, sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(1, 1))

	user := &model.User{
		Email:        "test@example.com",
		Username:     "testuser",
		PasswordHash: "hashedpassword",
	}

	err = repo.Create(context.Background(), user)
	require.NoError(t, err)
	assert.NotEmpty(t, user.ID)
	assert.Equal(t, "ACTIVE", user.Status)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_Create_Error(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	mock.ExpectExec("INSERT INTO users").
		WithArgs(sqlmock.AnyArg(), "testuser", "test@example.com", sqlmock.AnyArg(), "ACTIVE", false, sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnError(sql.ErrConnDone)

	user := &model.User{
		Email:        "test@example.com",
		Username:     "testuser",
		PasswordHash: "hashedpassword",
	}

	err = repo.Create(context.Background(), user)
	assert.Error(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_FindByID_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	rows := sqlmock.NewRows([]string{"id", "username", "email", "password_hash", "avatar_url", "status", "two_fa_enabled", "created_at", "updated_at"}).
		AddRow("user-123", "testuser", "test@example.com", "hash", "avatar.jpg", "ACTIVE", false, time.Now(), time.Now())

	mock.ExpectQuery("SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at FROM users WHERE id = \\$1").
		WithArgs("user-123").
		WillReturnRows(rows)

	user, err := repo.FindByID(context.Background(), "user-123")
	require.NoError(t, err)
	assert.Equal(t, "user-123", user.ID)
	assert.Equal(t, "testuser", user.Username)
	assert.Equal(t, "test@example.com", user.Email)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_FindByID_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	mock.ExpectQuery("SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at FROM users WHERE id = \\$1").
		WithArgs("nonexistent").
		WillReturnError(sql.ErrNoRows)

	user, err := repo.FindByID(context.Background(), "nonexistent")
	require.NoError(t, err)
	assert.Nil(t, user)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_FindByEmail_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	rows := sqlmock.NewRows([]string{"id", "username", "email", "password_hash", "avatar_url", "status", "two_fa_enabled", "created_at", "updated_at"}).
		AddRow("user-123", "testuser", "test@example.com", "hash", "", "ACTIVE", false, time.Now(), time.Now())

	mock.ExpectQuery("SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at FROM users WHERE email = \\$1").
		WithArgs("test@example.com").
		WillReturnRows(rows)

	user, err := repo.FindByEmail(context.Background(), "test@example.com")
	require.NoError(t, err)
	assert.Equal(t, "test@example.com", user.Email)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_FindByEmail_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	mock.ExpectQuery("SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at FROM users WHERE email = \\$1").
		WithArgs("nonexistent@example.com").
		WillReturnError(sql.ErrNoRows)

	user, err := repo.FindByEmail(context.Background(), "nonexistent@example.com")
	require.NoError(t, err)
	assert.Nil(t, user)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_FindByUsername_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	rows := sqlmock.NewRows([]string{"id", "username", "email", "password_hash", "avatar_url", "status", "two_fa_enabled", "created_at", "updated_at"}).
		AddRow("user-123", "testuser", "test@example.com", "hash", "", "ACTIVE", false, time.Now(), time.Now())

	mock.ExpectQuery("SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at FROM users WHERE username = \\$1").
		WithArgs("testuser").
		WillReturnRows(rows)

	user, err := repo.FindByUsername(context.Background(), "testuser")
	require.NoError(t, err)
	assert.Equal(t, "testuser", user.Username)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_UpdatePassword(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	mock.ExpectExec("UPDATE users SET password_hash = \\$1, updated_at = \\$2 WHERE id = \\$3").
		WithArgs("newhash", sqlmock.AnyArg(), "user-123").
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.UpdatePassword(context.Background(), "user-123", "newhash")
	require.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUserRepository_UpdateStatus(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewUserRepo(db)

	mock.ExpectExec("UPDATE users SET status = \\$1, updated_at = \\$2 WHERE id = \\$3").
		WithArgs("BANNED", sqlmock.AnyArg(), "user-123").
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.UpdateStatus(context.Background(), "user-123", "BANNED")
	require.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}
