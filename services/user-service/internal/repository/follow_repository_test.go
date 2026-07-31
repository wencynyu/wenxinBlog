package repository

import (
	"database/sql"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestFollowRepository_Follow_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	followerID := uuid.New()
	followingID := uuid.New()

	mock.ExpectExec("INSERT INTO follows").
		WithArgs(followerID, followingID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))

	inserted, err := repo.Follow(followerID, followingID)
	require.NoError(t, err)
	assert.True(t, inserted)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_Follow_AlreadyFollowing(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	followerID := uuid.New()
	followingID := uuid.New()

	// ON CONFLICT DO NOTHING 未插入 → RowsAffected = 0
	mock.ExpectExec("INSERT INTO follows").
		WithArgs(followerID, followingID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 0))

	inserted, err := repo.Follow(followerID, followingID)
	require.NoError(t, err)
	assert.False(t, inserted)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_Unfollow_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	followerID := uuid.New()
	followingID := uuid.New()

	mock.ExpectExec("DELETE FROM follows WHERE follower_id = \\$1 AND following_id = \\$2").
		WithArgs(followerID, followingID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	deleted, err := repo.Unfollow(followerID, followingID)
	require.NoError(t, err)
	assert.True(t, deleted)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_Unfollow_NotFollowing(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	followerID := uuid.New()
	followingID := uuid.New()

	// 未关注 → DELETE 影响 0 行
	mock.ExpectExec("DELETE FROM follows WHERE follower_id = \\$1 AND following_id = \\$2").
		WithArgs(followerID, followingID).
		WillReturnResult(sqlmock.NewResult(0, 0))

	deleted, err := repo.Unfollow(followerID, followingID)
	require.NoError(t, err)
	assert.False(t, deleted)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_IsFollowing_True(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	followerID := uuid.New()
	followingID := uuid.New()

	mock.ExpectQuery("SELECT EXISTS\\(SELECT 1 FROM follows WHERE follower_id = \\$1 AND following_id = \\$2\\)").
		WithArgs(followerID, followingID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))

	following, err := repo.IsFollowing(followerID, followingID)
	require.NoError(t, err)
	assert.True(t, following)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_IsFollowing_False(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	followerID := uuid.New()
	followingID := uuid.New()

	mock.ExpectQuery("SELECT EXISTS\\(SELECT 1 FROM follows WHERE follower_id = \\$1 AND following_id = \\$2\\)").
		WithArgs(followerID, followingID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(false))

	following, err := repo.IsFollowing(followerID, followingID)
	require.NoError(t, err)
	assert.False(t, following)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_GetFollowers_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	userID := uuid.New()

	// Mock count query
	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM follows WHERE following_id = \\$1").
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(5))

	// Mock data query
	rows := sqlmock.NewRows([]string{"id", "user_id", "display_name", "avatar_url", "bio", "website", "location", "company", "birthday", "view_count", "created_at", "updated_at"}).
		AddRow(uuid.New(), uuid.New(), sql.NullString{String: "Follower", Valid: true}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullString{Valid: false}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullTime{Valid: false}, 0, time.Now(), time.Now())

	mock.ExpectQuery("SELECT p\\.id, p\\.user_id, p\\.display_name, p\\.avatar_url, p\\.bio, p\\.website, p\\.location, p\\.company, p\\.birthday, p\\.view_count, p\\.created_at, p\\.updated_at FROM user_profiles p JOIN follows f ON p\\.user_id = f\\.follower_id WHERE f\\.following_id = \\$1").
		WithArgs(userID, 10, 0).
		WillReturnRows(rows)

	profiles, total, err := repo.GetFollowers(userID, 10, 0)
	require.NoError(t, err)
	assert.Equal(t, int64(5), total)
	assert.Equal(t, 1, len(profiles))
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_GetFollowing_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	userID := uuid.New()

	// Mock count query
	mock.ExpectQuery("SELECT COUNT\\(\\*\\) FROM follows WHERE follower_id = \\$1").
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(3))

	// Mock data query
	rows := sqlmock.NewRows([]string{"id", "user_id", "display_name", "avatar_url", "bio", "website", "location", "company", "birthday", "view_count", "created_at", "updated_at"}).
		AddRow(uuid.New(), uuid.New(), sql.NullString{String: "Following", Valid: true}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullString{Valid: false}, sql.NullString{Valid: false},
			sql.NullString{Valid: false}, sql.NullTime{Valid: false}, 0, time.Now(), time.Now())

	mock.ExpectQuery("SELECT p\\.id, p\\.user_id, p\\.display_name, p\\.avatar_url, p\\.bio, p\\.website, p\\.location, p\\.company, p\\.birthday, p\\.view_count, p\\.created_at, p\\.updated_at FROM user_profiles p JOIN follows f ON p\\.user_id = f\\.following_id WHERE f\\.follower_id = \\$1").
		WithArgs(userID, 10, 0).
		WillReturnRows(rows)

	profiles, total, err := repo.GetFollowing(userID, 10, 0)
	require.NoError(t, err)
	assert.Equal(t, int64(3), total)
	assert.Equal(t, 1, len(profiles))
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_GetFollowingIDs_Success(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	userID := uuid.New()
	id1 := uuid.New()
	id2 := uuid.New()

	rows := sqlmock.NewRows([]string{"following_id"}).
		AddRow(id1).
		AddRow(id2)

	mock.ExpectQuery("SELECT following_id FROM follows WHERE follower_id = \\$1").
		WithArgs(userID).
		WillReturnRows(rows)

	ids, err := repo.GetFollowingIDs(userID)
	require.NoError(t, err)
	assert.Equal(t, 2, len(ids))
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFollowRepository_GetFollowingIDs_Empty(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	repo := NewFollowRepository(db)
	userID := uuid.New()

	rows := sqlmock.NewRows([]string{"following_id"})

	mock.ExpectQuery("SELECT following_id FROM follows WHERE follower_id = \\$1").
		WithArgs(userID).
		WillReturnRows(rows)

	ids, err := repo.GetFollowingIDs(userID)
	require.NoError(t, err)
	assert.Equal(t, 0, len(ids))
	assert.NoError(t, mock.ExpectationsWereMet())
}
