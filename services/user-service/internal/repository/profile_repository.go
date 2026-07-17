package repository

import (
	"database/sql"
	"fmt"
	"time"

	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
)

// ProfileRepository defines the interface for profile data operations
type ProfileRepositoryInterface interface {
	Create(profile *model.UserProfile) error
	GetByUserID(userID uuid.UUID) (*model.UserProfile, error)
	GetByID(id uuid.UUID) (*model.UserProfile, error)
	GetUsername(userID uuid.UUID) (string, error)
	Update(userID uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error
	Search(query string, limit, offset int) ([]model.UserProfile, int64, error)
	IncrementViewCount(userID uuid.UUID) error
}

type ProfileRepository struct {
	db *sql.DB
}

// Ensure ProfileRepository implements ProfileRepositoryInterface
var _ ProfileRepositoryInterface = (*ProfileRepository)(nil)

func NewProfileRepository(db *sql.DB) *ProfileRepository {
	return &ProfileRepository{db: db}
}

func (r *ProfileRepository) Create(profile *model.UserProfile) error {
	// user_profiles.id 无 DB DEFAULT，在应用层生成 UUID 再插入（原实现靠 RETURNING id 取回，
	// 但列无默认值会触发 not-null 违约）。
	profile.ID = uuid.New()
	query := `INSERT INTO user_profiles (id, user_id, display_name, avatar_url, bio, website, location, company, birthday, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`
	_, err := r.db.Exec(query,
		profile.ID, profile.UserID, profile.DisplayName, profile.AvatarUrl, profile.Bio,
		profile.Website, profile.Location, profile.Company, profile.Birthday,
		time.Now(), time.Now(),
	)
	return err
}

// GetUsername 从同库的 users 表取 username，用于懒创建 profile 时填充 display_name 默认值。
func (r *ProfileRepository) GetUsername(userID uuid.UUID) (string, error) {
	var username string
	err := r.db.QueryRow(`SELECT username FROM users WHERE id = $1`, userID).Scan(&username)
	if err == sql.ErrNoRows {
		return "", nil
	}
	return username, err
}

func (r *ProfileRepository) GetByUserID(userID uuid.UUID) (*model.UserProfile, error) {
	p := &model.UserProfile{}
	query := `SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at
		FROM user_profiles WHERE user_id = $1`
	err := r.db.QueryRow(query, userID).Scan(
		&p.ID, &p.UserID, &p.DisplayName, &p.AvatarUrl, &p.Bio,
		&p.Website, &p.Location, &p.Company, &p.Birthday,
		&p.ViewCount, &p.CreatedAt, &p.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return p, err
}

func (r *ProfileRepository) GetByID(id uuid.UUID) (*model.UserProfile, error) {
	p := &model.UserProfile{}
	query := `SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at
		FROM user_profiles WHERE id = $1`
	err := r.db.QueryRow(query, id).Scan(
		&p.ID, &p.UserID, &p.DisplayName, &p.AvatarUrl, &p.Bio,
		&p.Website, &p.Location, &p.Company, &p.Birthday,
		&p.ViewCount, &p.CreatedAt, &p.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return p, err
}

func (r *ProfileRepository) Update(userID uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error {
	query := `UPDATE user_profiles SET
		display_name = COALESCE($1, display_name),
		avatar_url = COALESCE($2, avatar_url),
		bio = COALESCE($3, bio),
		website = COALESCE($4, website),
		location = COALESCE($5, location),
		company = COALESCE($6, company),
		birthday = COALESCE($7, birthday),
		updated_at = $8
		WHERE user_id = $9`
	result, err := r.db.Exec(query, displayName, avatarUrl, bio, website, location, company, birthday, time.Now(), userID)
	if err != nil {
		return err
	}
	rows, _ := result.RowsAffected()
	if rows == 0 {
		return fmt.Errorf("profile not found")
	}
	return nil
}

func (r *ProfileRepository) Search(query string, limit, offset int) ([]model.UserProfile, int64, error) {
	countQuery := `SELECT COUNT(*) FROM user_profiles WHERE to_tsvector('simple', display_name) @@ plainto_tsquery('simple', $1)`
	var total int64
	if err := r.db.QueryRow(countQuery, query).Scan(&total); err != nil {
		return nil, 0, err
	}

	dataQuery := `SELECT id, user_id, display_name, avatar_url, bio, website, location, company, birthday, view_count, created_at, updated_at
		FROM user_profiles WHERE to_tsvector('simple', display_name) @@ plainto_tsquery('simple', $1)
		ORDER BY view_count DESC LIMIT $2 OFFSET $3`
	rows, err := r.db.Query(dataQuery, query, limit, offset)
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
	return profiles, total, nil
}

func (r *ProfileRepository) IncrementViewCount(userID uuid.UUID) error {
	_, err := r.db.Exec(`UPDATE user_profiles SET view_count = view_count + 1 WHERE user_id = $1`, userID)
	return err
}
