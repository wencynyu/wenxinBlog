package repository

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/alicebob/miniredis/v2"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestStatsRepository_GetStats_FromDB(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	// Setup miniredis
	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	// Mock database query (cache miss)
	rows := sqlmock.NewRows([]string{"user_id", "post_count", "follower_count", "following_count", "like_count", "updated_at"}).
		AddRow(userID, 10, 100, 50, 200, time.Now())

	mock.ExpectQuery("SELECT user_id, post_count, follower_count, following_count, like_count, updated_at FROM user_stats WHERE user_id = \\$1").
		WithArgs(userID).
		WillReturnRows(rows)

	stats, err := repo.GetStats(userID)
	require.NoError(t, err)
	assert.Equal(t, 10, stats.PostCount)
	assert.Equal(t, 100, stats.FollowerCount)
	assert.Equal(t, 50, stats.FollowingCount)
	assert.Equal(t, 200, stats.LikeCount)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_GetStats_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	mock.ExpectQuery("SELECT user_id, post_count, follower_count, following_count, like_count, updated_at FROM user_stats WHERE user_id = \\$1").
		WithArgs(userID).
		WillReturnError(sql.ErrNoRows)

	stats, err := repo.GetStats(userID)
	require.NoError(t, err)
	assert.Equal(t, userID, stats.UserID)
	assert.Equal(t, 0, stats.PostCount)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_GetStats_FromCache(t *testing.T) {
	db, _, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	// Pre-populate cache
	ctx := context.Background()
	key := "user:" + userID.String() + ":stats"
	rdb.HSet(ctx, key, map[string]interface{}{
		"post_count":      "15",
		"follower_count":  "150",
		"following_count": "75",
		"like_count":      "300",
	})
	rdb.Expire(ctx, key, 5*time.Minute)

	stats, err := repo.GetStats(userID)
	require.NoError(t, err)
	assert.Equal(t, 15, stats.PostCount)
	assert.Equal(t, 150, stats.FollowerCount)
	assert.Equal(t, 75, stats.FollowingCount)
	assert.Equal(t, 300, stats.LikeCount)
}

func TestStatsRepository_UpsertStats(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	mock.ExpectExec("INSERT INTO user_stats").
		WithArgs(userID, 5, 25, 10, 50, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.UpsertStats(userID, 5, 25, 10, 50)
	require.NoError(t, err)

	// Wait for async cache invalidation
	time.Sleep(100 * time.Millisecond)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_IncrementFollowerCount(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	mock.ExpectExec("INSERT INTO user_stats").
		WithArgs(userID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.IncrementFollowerCount(userID)
	require.NoError(t, err)

	time.Sleep(100 * time.Millisecond)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_DecrementFollowerCount(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	mock.ExpectExec("UPDATE user_stats SET follower_count = GREATEST\\(follower_count - 1, 0\\), updated_at = \\$1 WHERE user_id = \\$2").
		WithArgs(sqlmock.AnyArg(), userID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.DecrementFollowerCount(userID)
	require.NoError(t, err)

	time.Sleep(100 * time.Millisecond)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_IncrementFollowingCount(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	mock.ExpectExec("INSERT INTO user_stats").
		WithArgs(userID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.IncrementFollowingCount(userID)
	require.NoError(t, err)

	time.Sleep(100 * time.Millisecond)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_DecrementFollowingCount(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	mock.ExpectExec("UPDATE user_stats SET following_count = GREATEST\\(following_count - 1, 0\\), updated_at = \\$1 WHERE user_id = \\$2").
		WithArgs(sqlmock.AnyArg(), userID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.DecrementFollowingCount(userID)
	require.NoError(t, err)

	time.Sleep(100 * time.Millisecond)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestStatsRepository_CacheInvalidation(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	s := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: s.Addr()})

	repo := NewStatsRepository(db, rdb)
	userID := uuid.New()

	// First, populate cache
	ctx := context.Background()
	key := "user:" + userID.String() + ":stats"
	rdb.HSet(ctx, key, map[string]interface{}{
		"post_count":     "10",
		"follower_count": "100",
	})
	rdb.Expire(ctx, key, 5*time.Minute)

	// Verify cache exists
	val := rdb.Exists(ctx, key).Val()
	assert.Equal(t, int64(1), val)

	// Update stats (should invalidate cache)
	mock.ExpectExec("INSERT INTO user_stats").
		WithArgs(userID, 5, 25, 10, 50, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err = repo.UpsertStats(userID, 5, 25, 10, 50)
	require.NoError(t, err)

	// Wait for async operation
	time.Sleep(200 * time.Millisecond)

	// Verify cache is cleared
	val = rdb.Exists(ctx, key).Val()
	assert.Equal(t, int64(0), val) // Cache should be invalidated
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestParseCachedStats(t *testing.T) {
	userID := uuid.New()
	cached := map[string]string{
		"post_count":      "20",
		"follower_count":  "200",
		"following_count": "80",
		"like_count":      "400",
	}

	stats, err := parseCachedStats(userID, cached)
	require.NoError(t, err)
	assert.Equal(t, userID, stats.UserID)
	assert.Equal(t, 20, stats.PostCount)
	assert.Equal(t, 200, stats.FollowerCount)
	assert.Equal(t, 80, stats.FollowingCount)
	assert.Equal(t, 400, stats.LikeCount)
}

func TestParseCachedStats_Partial(t *testing.T) {
	userID := uuid.New()
	cached := map[string]string{
		"post_count": "30",
		// Other fields missing
	}

	stats, err := parseCachedStats(userID, cached)
	require.NoError(t, err)
	assert.Equal(t, userID, stats.UserID)
	assert.Equal(t, 30, stats.PostCount)
	assert.Equal(t, 0, stats.FollowerCount) // Default
}
