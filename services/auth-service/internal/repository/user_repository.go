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
	FindByID(ctx context.Context, id string) (*model.User, error)
	FindByEmail(ctx context.Context, email string) (*model.User, error)
	FindByUsername(ctx context.Context, username string) (*model.User, error)
	UpdatePassword(ctx context.Context, id, hash string) error
	UpdateStatus(ctx context.Context, id, status string) error
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
