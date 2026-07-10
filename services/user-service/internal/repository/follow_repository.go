package repository

import (
	"database/sql"
	"time"

	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
)

// FollowRepositoryInterface defines the interface for follow operations
type FollowRepositoryInterface interface {
	Follow(followerID, followingID uuid.UUID) error
	Unfollow(followerID, followingID uuid.UUID) error
	IsFollowing(followerID, followingID uuid.UUID) (bool, error)
	GetFollowers(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error)
	GetFollowing(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error)
	GetFollowingIDs(userID uuid.UUID) ([]uuid.UUID, error)
}

type FollowRepository struct {
	db *sql.DB
}

// Ensure FollowRepository implements FollowRepositoryInterface
var _ FollowRepositoryInterface = (*FollowRepository)(nil)

func NewFollowRepository(db *sql.DB) *FollowRepository {
	return &FollowRepository{db: db}
}

func (r *FollowRepository) Follow(followerID, followingID uuid.UUID) error {
	query := `INSERT INTO follows (follower_id, following_id, created_at) VALUES ($1, $2, $3)
		ON CONFLICT (follower_id, following_id) DO NOTHING`
	_, err := r.db.Exec(query, followerID, followingID, time.Now())
	return err
}

func (r *FollowRepository) Unfollow(followerID, followingID uuid.UUID) error {
	_, err := r.db.Exec(`DELETE FROM follows WHERE follower_id = $1 AND following_id = $2`, followerID, followingID)
	return err
}

func (r *FollowRepository) IsFollowing(followerID, followingID uuid.UUID) (bool, error) {
	var exists bool
	err := r.db.QueryRow(
		`SELECT EXISTS(SELECT 1 FROM follows WHERE follower_id = $1 AND following_id = $2)`,
		followerID, followingID,
	).Scan(&exists)
	return exists, err
}

func (r *FollowRepository) GetFollowers(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error) {
	var total int64
	if err := r.db.QueryRow(`SELECT COUNT(*) FROM follows WHERE following_id = $1`, userID).Scan(&total); err != nil {
		return nil, 0, err
	}

	query := `SELECT p.id, p.user_id, p.display_name, p.avatar_url, p.bio, p.website, p.location, p.company, p.birthday, p.view_count, p.created_at, p.updated_at
		FROM user_profiles p JOIN follows f ON p.user_id = f.follower_id
		WHERE f.following_id = $1 ORDER BY f.created_at DESC LIMIT $2 OFFSET $3`
	return r.queryProfiles(query, userID, limit, offset)
}

func (r *FollowRepository) GetFollowing(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error) {
	var total int64
	if err := r.db.QueryRow(`SELECT COUNT(*) FROM follows WHERE follower_id = $1`, userID).Scan(&total); err != nil {
		return nil, 0, err
	}

	query := `SELECT p.id, p.user_id, p.display_name, p.avatar_url, p.bio, p.website, p.location, p.company, p.birthday, p.view_count, p.created_at, p.updated_at
		FROM user_profiles p JOIN follows f ON p.user_id = f.following_id
		WHERE f.follower_id = $1 ORDER BY f.created_at DESC LIMIT $2 OFFSET $3`
	return r.queryProfiles(query, userID, limit, offset)
}

func (r *FollowRepository) GetFollowingIDs(userID uuid.UUID) ([]uuid.UUID, error) {
	rows, err := r.db.Query(`SELECT following_id FROM follows WHERE follower_id = $1`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var ids []uuid.UUID
	for rows.Next() {
		var id uuid.UUID
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, nil
}

func (r *FollowRepository) queryProfiles(query string, args ...interface{}) ([]model.UserProfile, int64, error) {
	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var profiles []model.UserProfile
	for rows.Next() {
		var p model.UserProfile
		if err := rows.Scan(&p.ID, &p.UserID, &p.DisplayName, &p.AvatarUrl, &p.Bio,
			&p.Website, &p.Location, &p.Company, &p.Birthday,
			&p.ViewCount, &p.CreatedAt, &p.UpdatedAt); err != nil {
			return nil, 0, err
		}
		profiles = append(profiles, p)
	}
	return profiles, 0, nil
}
