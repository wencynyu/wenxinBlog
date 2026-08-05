package repository

import (
	"database/sql"
	"errors"
	"fmt"
	"time"

	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
)

// ProfileRepository defines the interface for profile data operations
type ProfileRepositoryInterface interface {
	Create(profile *model.UserProfile) error
	CreateUser(userID uuid.UUID, username, email string) error
	GetByUserID(userID uuid.UUID) (*model.UserProfile, error)
	GetByID(id uuid.UUID) (*model.UserProfile, error)
	GetUsername(userID uuid.UUID) (string, error)
	GetUserinfo(userID uuid.UUID) (username, email string, err error)
	Update(userID uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error
	Search(query string, limit, offset int) ([]model.UserProfile, int64, error)
	IncrementViewCount(userID uuid.UUID) error
}

// ErrUserNotFound 用户不存在。懒创建 profile 前先用它确认 user 存在，
// 避免对不存在的 user 插入 user_profiles 触发外键违约。
var ErrUserNotFound = errors.New("user not found")

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

// CreateUser 幂等把注册用户插入 users 表（auth-service 注册成功后跨库调用）。
// email/password_hash 在本库已改为可空：社交/手机号用户无邮箱，空串经 NULLIF 转为 NULL，
// 配合部分唯一索引（users_email_unique WHERE email IS NOT NULL）允许多个无邮箱用户。
// password_hash 在本库永不被校验，置空串占位即可。status/two_fa_enabled 走 DB 默认值。
func (r *ProfileRepository) CreateUser(userID uuid.UUID, username, email string) error {
	query := `INSERT INTO users (id, username, email, password_hash, created_at, updated_at)
		VALUES ($1, $2, NULLIF($3, ''), '', $4, $5)
		ON CONFLICT (id) DO NOTHING`
	_, err := r.db.Exec(query, userID, username, email, time.Now(), time.Now())
	return err
}

// GetUsername 从同库的 users 表取 username，用于懒创建 profile 时填充 display_name 默认值。
func (r *ProfileRepository) GetUsername(userID uuid.UUID) (string, error) {
	var username string
	err := r.db.QueryRow(`SELECT username FROM users WHERE id = $1`, userID).Scan(&username)
	if err == sql.ErrNoRows {
		return "", ErrUserNotFound
	}
	return username, err
}

// GetUserinfo 取 username + email（同库 users 表），供 profile 响应补字段。
func (r *ProfileRepository) GetUserinfo(userID uuid.UUID) (string, string, error) {
	var username, email string
	err := r.db.QueryRow(`SELECT username, email FROM users WHERE id = $1`, userID).Scan(&username, &email)
	if err == sql.ErrNoRows {
		return "", "", ErrUserNotFound
	}
	return username, email, err
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
