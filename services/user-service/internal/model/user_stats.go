package model

import (
	"time"

	"github.com/google/uuid"
)

type UserStats struct {
	UserID         uuid.UUID `json:"user_id"`
	PostCount      int       `json:"post_count"`
	FollowerCount  int       `json:"follower_count"`
	FollowingCount int       `json:"following_count"`
	LikeCount      int       `json:"like_count"`
	UpdatedAt      time.Time `json:"updated_at"`
}
