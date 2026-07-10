package model

import (
	"database/sql"
	"time"

	"github.com/google/uuid"
)

type UserProfile struct {
	ID          uuid.UUID       `json:"id"`
	UserID      uuid.UUID       `json:"user_id"`
	DisplayName sql.NullString  `json:"display_name"`
	AvatarUrl   sql.NullString  `json:"avatar_url"`
	Bio         sql.NullString  `json:"bio"`
	Website     sql.NullString  `json:"website"`
	Location    sql.NullString  `json:"location"`
	Company     sql.NullString  `json:"company"`
	Birthday    sql.NullTime    `json:"birthday"`
	ViewCount   int64           `json:"view_count"`
	CreatedAt   time.Time       `json:"created_at"`
	UpdatedAt   time.Time       `json:"updated_at"`
}
