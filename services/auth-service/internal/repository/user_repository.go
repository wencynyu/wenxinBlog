package repository

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"wenxinblog/auth-service/internal/model"

	"github.com/google/uuid"
)

// UserRepository defines the interface for user data operations
type UserRepository interface {
	Create(ctx context.Context, user *model.User) error
	// CreateNoPassword 创建无密码用户（社交/手机号登录）：email/password_hash 可空。
	CreateNoPassword(ctx context.Context, user *model.User) error
	FindByID(ctx context.Context, id string) (*model.User, error)
	FindByEmail(ctx context.Context, email string) (*model.User, error)
	FindByUsername(ctx context.Context, username string) (*model.User, error)
	// FindByPhone 按手机号查（手机号验证码登录）。phone 非空的行才会命中。
	FindByPhone(ctx context.Context, phone string) (*model.User, error)
	UpdatePassword(ctx context.Context, id, hash string) error
	UpdateStatus(ctx context.Context, id, status string) error
	// ListUsers 分页查询用户（search 模糊匹配 username/email），不返回 password_hash。
	ListUsers(ctx context.Context, page, pageSize int, search string) ([]model.User, int64, error)
}

type UserRepo struct {
	db *sql.DB
}

// Ensure UserRepo implements UserRepository
var _ UserRepository = (*UserRepo)(nil)

func NewUserRepo(db *sql.DB) *UserRepo {
	return &UserRepo{db: db}
}

func (r *UserRepo) Create(ctx context.Context, user *model.User) error {
	user.ID = uuid.New().String()
	user.CreatedAt = time.Now()
	user.UpdatedAt = time.Now()
	user.Status = "ACTIVE"
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO users (id, username, email, password_hash, status, two_fa_enabled, created_at, updated_at)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
		user.ID, user.Username, user.Email, user.PasswordHash, user.Status, user.TwoFAEnabled,
		user.CreatedAt, user.UpdatedAt)
	return err
}

// CreateNoPassword 创建无密码用户（社交/手机号登录）。
// email/password_hash/phone 用 NULLIF 把空串转成 NULL，配合部分唯一索引
// （多 NULL 不冲突，从而允许多个无邮箱/无手机号的社交用户）。
func (r *UserRepo) CreateNoPassword(ctx context.Context, user *model.User) error {
	user.ID = uuid.New().String()
	user.CreatedAt = time.Now()
	user.UpdatedAt = time.Now()
	if user.Status == "" {
		user.Status = "ACTIVE"
	}
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO users (id, username, email, password_hash, avatar_url, phone, status, two_fa_enabled, created_at, updated_at)
		 VALUES ($1, $2, NULLIF($3,''), NULLIF($4,''), $5, NULLIF($6,''), $7, $8, $9, $10)`,
		user.ID, user.Username, user.Email, user.PasswordHash, user.AvatarURL, user.Phone,
		user.Status, user.TwoFAEnabled, user.CreatedAt, user.UpdatedAt)
	return err
}

// FindByPhone 按手机号查询用户（仅命中 phone 非空的行，故 phone 列扫描为 string 安全）。
func (r *UserRepo) FindByPhone(ctx context.Context, phone string) (*model.User, error) {
	u := &model.User{}
	err := r.db.QueryRowContext(ctx,
		`SELECT id, username, email, password_hash, avatar_url, phone, status, two_fa_enabled, created_at, updated_at
		 FROM users WHERE phone = $1`, phone).
		Scan(&u.ID, &u.Username, &u.Email, &u.PasswordHash, &u.AvatarURL, &u.Phone, &u.Status, &u.TwoFAEnabled,
			&u.CreatedAt, &u.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return u, err
}

func (r *UserRepo) FindByID(ctx context.Context, id string) (*model.User, error) {
	u := &model.User{}
	err := r.db.QueryRowContext(ctx,
		`SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at
		 FROM users WHERE id = $1`, id).
		Scan(&u.ID, &u.Username, &u.Email, &u.PasswordHash, &u.AvatarURL, &u.Status, &u.TwoFAEnabled,
			&u.CreatedAt, &u.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return u, err
}

func (r *UserRepo) FindByEmail(ctx context.Context, email string) (*model.User, error) {
	u := &model.User{}
	err := r.db.QueryRowContext(ctx,
		`SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at
		 FROM users WHERE email = $1`, email).
		Scan(&u.ID, &u.Username, &u.Email, &u.PasswordHash, &u.AvatarURL, &u.Status, &u.TwoFAEnabled,
			&u.CreatedAt, &u.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return u, err
}

func (r *UserRepo) FindByUsername(ctx context.Context, username string) (*model.User, error) {
	u := &model.User{}
	err := r.db.QueryRowContext(ctx,
		`SELECT id, username, email, password_hash, avatar_url, status, two_fa_enabled, created_at, updated_at
		 FROM users WHERE username = $1`, username).
		Scan(&u.ID, &u.Username, &u.Email, &u.PasswordHash, &u.AvatarURL, &u.Status, &u.TwoFAEnabled,
			&u.CreatedAt, &u.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return u, err
}

func (r *UserRepo) UpdatePassword(ctx context.Context, id, hash string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE users SET password_hash = $1, updated_at = $2 WHERE id = $3`, hash, time.Now(), id)
	return err
}

func (r *UserRepo) UpdateStatus(ctx context.Context, id, status string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE users SET status = $1, updated_at = $2 WHERE id = $3`, status, time.Now(), id)
	return err
}

const listUsersSQL = `
SELECT id, username, email, avatar_url, status, two_fa_enabled, created_at, updated_at
FROM users
WHERE ($1 = '' OR username ILIKE '%' || $1 || '%' OR email ILIKE '%' || $1 || '%')
ORDER BY created_at DESC
LIMIT $2 OFFSET $3`

const countUsersSQL = `
SELECT count(*) FROM users
WHERE ($1 = '' OR username ILIKE '%' || $1 || '%' OR email ILIKE '%' || $1 || '%')`

// ListUsers 分页查询用户（不含 password_hash），返回 (users, total)。
func (r *UserRepo) ListUsers(ctx context.Context, page, pageSize int, search string) ([]model.User, int64, error) {
	offset := (page - 1) * pageSize
	if offset < 0 {
		offset = 0
	}
	rows, err := r.db.QueryContext(ctx, listUsersSQL, search, pageSize, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	users := []model.User{}
	for rows.Next() {
		var u model.User
		if err := rows.Scan(&u.ID, &u.Username, &u.Email, &u.AvatarURL, &u.Status, &u.TwoFAEnabled, &u.CreatedAt, &u.UpdatedAt); err != nil {
			return nil, 0, err
		}
		users = append(users, u)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	var total int64
	if err := r.db.QueryRowContext(ctx, countUsersSQL, search).Scan(&total); err != nil {
		return nil, 0, err
	}
	return users, total, nil
}
