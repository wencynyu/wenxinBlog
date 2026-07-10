package model

import (
	"time"

	"github.com/google/uuid"
)

type Follow struct {
	ID         uuid.UUID `json:"id"`
	FollowerID uuid.UUID `json:"follower_id"`
	FollowingID uuid.UUID `json:"following_id"`
	CreatedAt  time.Time `json:"created_at"`
}
