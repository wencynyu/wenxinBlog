package repository

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

// StatsRepositoryInterface defines the interface for stats operations
type StatsRepositoryInterface interface {
	GetStats(userID uuid.UUID) (*model.UserStats, error)
	UpsertStats(userID uuid.UUID, postCount, followerCount, followingCount, likeCount int) error
	IncrementFollowerCount(userID uuid.UUID) error
	DecrementFollowerCount(userID uuid.UUID) error
	IncrementFollowingCount(userID uuid.UUID) error
	DecrementFollowingCount(userID uuid.UUID) error
}

type StatsRepository struct {
	db    *sql.DB
	redis *redis.Client
}

// Ensure StatsRepository implements StatsRepositoryInterface
var _ StatsRepositoryInterface = (*StatsRepository)(nil)

func NewStatsRepository(db *sql.DB, rdb *redis.Client) *StatsRepository {
	return &StatsRepository{db: db, redis: rdb}
}

func (r *StatsRepository) GetStats(userID uuid.UUID) (*model.UserStats, error) {
	ctx := context.Background()
	key := fmt.Sprintf("user:%s:stats", userID)

	// Try cache first
	cached, err := r.redis.HGetAll(ctx, key).Result()
	if err == nil && len(cached) > 0 {
		return parseCachedStats(userID, cached)
	}

	// Fall back to DB
	stats := &model.UserStats{}
	query := `SELECT user_id, post_count, follower_count, following_count, like_count, updated_at
		FROM user_stats WHERE user_id = $1`
	err = r.db.QueryRow(query, userID).Scan(
		&stats.UserID, &stats.PostCount, &stats.FollowerCount,
		&stats.FollowingCount, &stats.LikeCount, &stats.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return &model.UserStats{UserID: userID}, nil
	}
	if err != nil {
		return nil, err
	}

	// Cache result (5 min TTL)
	go r.cacheStats(stats)

	return stats, nil
}

func (r *StatsRepository) UpsertStats(userID uuid.UUID, postCount, followerCount, followingCount, likeCount int) error {
	query := `INSERT INTO user_stats (user_id, post_count, follower_count, following_count, like_count, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (user_id) DO UPDATE SET
			post_count = COALESCE($2, post_count),
			follower_count = COALESCE($3, follower_count),
			following_count = COALESCE($4, following_count),
			like_count = COALESCE($5, like_count),
			updated_at = $6`
	_, err := r.db.Exec(query, userID, postCount, followerCount, followingCount, likeCount, time.Now())
	if err == nil {
		go r.invalidateCache(userID)
	}
	return err
}

func (r *StatsRepository) IncrementFollowerCount(userID uuid.UUID) error {
	query := `INSERT INTO user_stats (user_id, follower_count, updated_at)
		VALUES ($1, 1, $2)
		ON CONFLICT (user_id) DO UPDATE SET
			follower_count = user_stats.follower_count + 1,
			updated_at = $2`
	_, err := r.db.Exec(query, userID, time.Now())
	if err == nil {
		go r.invalidateCache(userID)
	}
	return err
}

func (r *StatsRepository) DecrementFollowerCount(userID uuid.UUID) error {
	_, err := r.db.Exec(`UPDATE user_stats SET follower_count = GREATEST(follower_count - 1, 0), updated_at = $1 WHERE user_id = $2`, time.Now(), userID)
	if err == nil {
		go r.invalidateCache(userID)
	}
	return err
}

func (r *StatsRepository) IncrementFollowingCount(userID uuid.UUID) error {
	query := `INSERT INTO user_stats (user_id, following_count, updated_at)
		VALUES ($1, 1, $2)
		ON CONFLICT (user_id) DO UPDATE SET
			following_count = user_stats.following_count + 1,
			updated_at = $2`
	_, err := r.db.Exec(query, userID, time.Now())
	if err == nil {
		go r.invalidateCache(userID)
	}
	return err
}

func (r *StatsRepository) DecrementFollowingCount(userID uuid.UUID) error {
	_, err := r.db.Exec(`UPDATE user_stats SET following_count = GREATEST(following_count - 1, 0), updated_at = $1 WHERE user_id = $2`, time.Now(), userID)
	if err == nil {
		go r.invalidateCache(userID)
	}
	return err
}

func (r *StatsRepository) cacheStats(stats *model.UserStats) {
	ctx := context.Background()
	key := fmt.Sprintf("user:%s:stats", stats.UserID)
	data := map[string]interface{}{
		"post_count":      stats.PostCount,
		"follower_count":  stats.FollowerCount,
		"following_count": stats.FollowingCount,
		"like_count":      stats.LikeCount,
	}
	r.redis.HSet(ctx, key, data)
	r.redis.Expire(ctx, key, 5*time.Minute)
}

func (r *StatsRepository) invalidateCache(userID uuid.UUID) {
	ctx := context.Background()
	r.redis.Del(ctx, fmt.Sprintf("user:%s:stats", userID))
}

func parseCachedStats(userID uuid.UUID, cached map[string]string) (*model.UserStats, error) {
	stats := &model.UserStats{UserID: userID}
	if v, ok := cached["post_count"]; ok {
		fmt.Sscanf(v, "%d", &stats.PostCount)
	}
	if v, ok := cached["follower_count"]; ok {
		fmt.Sscanf(v, "%d", &stats.FollowerCount)
	}
	if v, ok := cached["following_count"]; ok {
		fmt.Sscanf(v, "%d", &stats.FollowingCount)
	}
	if v, ok := cached["like_count"]; ok {
		fmt.Sscanf(v, "%d", &stats.LikeCount)
	}
	return stats, nil
}

// Suppress unused import warning
var _ = json.Marshal
