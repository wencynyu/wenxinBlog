package repository

import (
	"database/sql"
	"testing"
	"time"

	"wenxinblog/user-service/internal/model"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestProfileRepository_Create_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)

	userID := uuid.New()
	mock.ExpectExec("INSERT INTO user_profiles").
		WithArgs(sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))

	profile := &model.UserProfile{
		UserID:      userID,
		DisplayName: sql.NullString{String: "Test User", Valid: true},
	}

	err = repo.Create(profile)
	require.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_GetByUserID_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)
	userID := uuid.New()
	profileID := uuid.New()

	rows := sqlmock.NewRows([]string{"id", "user_id", "display_name", "avatar_url", "bio", "website", "location", "company", "birthday", "view_count", "created_at", "updated_at"}).
		AddRow(profileID, userID, sql.NullString{String: "Test User", Valid: true}, sql.NullString{Valid: false},
			sql.NullString{String: "Test bio", Valid: true}, sql.NullString{Valid: false}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullTime{Valid: false}, 100, time.Now(), time.Now())

	mock.ExpectQuery("SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at FROM user_profiles WHERE user_id = \\$1").
		WithArgs(userID).
		WillReturnRows(rows)

	profile, err := repo.GetByUserID(userID)
	require.NoError(t, err)
	assert.Equal(t, userID, profile.UserID)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_GetByUserID_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)
	userID := uuid.New()

	mock.ExpectQuery("SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at FROM user_profiles WHERE user_id = \\$1").
		WithArgs(userID).
		WillReturnError(sql.ErrNoRows)

	profile, err := repo.GetByUserID(userID)
	require.NoError(t, err)
	assert.Nil(t, profile)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_GetByID_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)
	profileID := uuid.New()
	userID := uuid.New()

	rows := sqlmock.NewRows([]string{"id", "user_id", "display_name", "avatar_url", "bio", "website", "location", "company", "birthday", "view_count", "created_at", "updated_at"}).
		AddRow(profileID, userID, sql.NullString{String: "Test", Valid: true}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullString{Valid: false}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullTime{Valid: false}, 0, time.Now(), time.Now())

	mock.ExpectQuery("SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at FROM user_profiles WHERE id = \\$1").
		WithArgs(profileID).
		WillReturnRows(rows)

	profile, err := repo.GetByID(profileID)
	require.NoError(t, err)
	assert.Equal(t, profileID, profile.ID)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_Update_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)
	userID := uuid.New()

	displayName := "Updated Name"
	mock.ExpectExec("UPDATE user_profiles SET").
		WithArgs(&displayName, sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), userID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.Update(userID, &displayName, nil, nil, nil, nil, nil, nil)
	require.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_Update_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)
	userID := uuid.New()

	mock.ExpectExec("UPDATE user_profiles SET").
		WithArgs(sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), userID).
		WillReturnResult(sqlmock.NewResult(0, 0))

	err = repo.Update(userID, nil, nil, nil, nil, nil, nil, nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "profile not found")
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_Search_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)

	// Mock count query
	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM user_profiles WHERE to_tsvector").
		WithArgs("test").
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(5))

	// Mock data query
	rows := sqlmock.NewRows([]string{"id", "user_id", "display_name", "avatar_url", "bio", "website", "location", "company", "birthday", "view_count", "created_at", "updated_at"}).
		AddRow(uuid.New(), uuid.New(), sql.NullString{String: "Test User", Valid: true}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullString{Valid: false}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullTime{Valid: false}, 0, time.Now(), time.Now())

	mock.ExpectQuery("SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at FROM user_profiles WHERE to_tsvector").
		WithArgs("test", 10, 0).
		WillReturnRows(rows)

	profiles, total, err := repo.Search("test", 10, 0)
	require.NoError(t, err)
	assert.Equal(t, int64(5), total)
	assert.Equal(t, 1, len(profiles))
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestProfileRepository_IncrementViewCount(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewProfileRepository(db)
	userID := uuid.New()

	mock.ExpectExec("UPDATE user_profiles SET view_count = view_count \\+ 1 WHERE user_id = \\$1").
		WithArgs(userID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.IncrementViewCount(userID)
	require.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}
