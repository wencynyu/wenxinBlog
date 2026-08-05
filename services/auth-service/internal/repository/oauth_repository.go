package repository

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"wenxinblog/auth-service/internal/model"

	"github.com/google/uuid"
)

// OAuthAccountRepository 第三方账号绑定表的读写。
type OAuthAccountRepository interface {
	// FindByProvider 按 (provider, provider_user_id) 查绑定记录。未找到返回 (nil, nil)。
	FindByProvider(ctx context.Context, provider, providerUserID string) (*model.OAuthAccount, error)
	// Create 新建一条绑定。UNIQUE(provider, provider_user_id) 冲突时返回 pq 23505 错误。
	Create(ctx context.Context, acc *model.OAuthAccount) error
	// DeleteByUserAndProvider 删除某用户的某个 provider 绑定。
	DeleteByUserAndProvider(ctx context.Context, userID, provider string) error
	// ListByUser 列出某用户的所有绑定。
	ListByUser(ctx context.Context, userID string) ([]model.OAuthAccount, error)
}

type OAuthAccountRepo struct {
	db *sql.DB
}

var _ OAuthAccountRepository = (*OAuthAccountRepo)(nil)

func NewOAuthAccountRepo(db *sql.DB) *OAuthAccountRepo {
	return &OAuthAccountRepo{db: db}
}

const oauthAccountSelectCols = `id, user_id, provider, provider_user_id, raw_openid, access_token, refresh_token, expires_at, created_at, updated_at`

// scanOAuthAccount 把一行扫描进 acc（可空列用 Null* 接收后回填）。
func scanOAuthAccount(sc func(dest ...interface{}) error, acc *model.OAuthAccount) error {
	var rawOpenID, accessToken, refreshToken sql.NullString
	var expiresAt sql.NullTime
	if err := sc(&acc.ID, &acc.UserID, &acc.Provider, &acc.ProviderUserID,
		&rawOpenID, &accessToken, &refreshToken, &expiresAt, &acc.CreatedAt, &acc.UpdatedAt); err != nil {
		return err
	}
	acc.RawOpenID = rawOpenID.String
	acc.AccessToken = accessToken.String
	acc.RefreshToken = refreshToken.String
	if expiresAt.Valid {
		acc.ExpiresAt = expiresAt.Time
	}
	return nil
}

func (r *OAuthAccountRepo) FindByProvider(ctx context.Context, provider, providerUserID string) (*model.OAuthAccount, error) {
	acc := &model.OAuthAccount{}
	row := r.db.QueryRowContext(ctx,
		`SELECT `+oauthAccountSelectCols+` FROM oauth_accounts WHERE provider = $1 AND provider_user_id = $2`,
		provider, providerUserID)
	if err := scanOAuthAccount(row.Scan, acc); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return acc, nil
}

// Create 插入绑定。expires_at 为零值时存 NULL；空 token 串存 NULL。
func (r *OAuthAccountRepo) Create(ctx context.Context, acc *model.OAuthAccount) error {
	acc.ID = uuid.New().String()
	now := time.Now()
	acc.CreatedAt = now
	acc.UpdatedAt = now
	var expiresAt interface{}
	if acc.ExpiresAt.IsZero() {
		expiresAt = nil
	} else {
		expiresAt = acc.ExpiresAt
	}
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO oauth_accounts (id, user_id, provider, provider_user_id, raw_openid, access_token, refresh_token, expires_at, created_at, updated_at)
		 VALUES ($1, $2, $3, $4, NULLIF($5,''), NULLIF($6,''), NULLIF($7,''), $8, $9, $10)`,
		acc.ID, acc.UserID, acc.Provider, acc.ProviderUserID,
		acc.RawOpenID, acc.AccessToken, acc.RefreshToken, expiresAt, acc.CreatedAt, acc.UpdatedAt)
	return err
}

func (r *OAuthAccountRepo) DeleteByUserAndProvider(ctx context.Context, userID, provider string) error {
	_, err := r.db.ExecContext(ctx,
		`DELETE FROM oauth_accounts WHERE user_id = $1 AND provider = $2`, userID, provider)
	return err
}

func (r *OAuthAccountRepo) ListByUser(ctx context.Context, userID string) ([]model.OAuthAccount, error) {
	rows, err := r.db.QueryContext(ctx,
		`SELECT `+oauthAccountSelectCols+` FROM oauth_accounts WHERE user_id = $1 ORDER BY created_at`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	accounts := []model.OAuthAccount{}
	for rows.Next() {
		var acc model.OAuthAccount
		if err := scanOAuthAccount(rows.Scan, &acc); err != nil {
			return nil, err
		}
		accounts = append(accounts, acc)
	}
	return accounts, rows.Err()
}
