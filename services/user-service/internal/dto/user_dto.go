package dto

import (
	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
)

type APIResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

func Success(data interface{}) APIResponse {
	return APIResponse{Code: 0, Message: "ok", Data: data}
}

func Error(msg string) APIResponse {
	return APIResponse{Code: -1, Message: msg}
}

type UpdateProfileRequest struct {
	DisplayName *string `json:"display_name"`
	AvatarUrl   *string `json:"avatar_url"`
	Bio         *string `json:"bio"`
	Website     *string `json:"website"`
	Location    *string `json:"location"`
	Company     *string `json:"company"`
	Birthday    *string `json:"birthday"`
}

type UserProfileResponse struct {
	ID          uuid.UUID `json:"id"`
	UserID      uuid.UUID `json:"user_id"`
	DisplayName string    `json:"display_name"`
	AvatarUrl   string    `json:"avatar_url"`
	Bio         string    `json:"bio"`
	Website     string    `json:"website"`
	Location    string    `json:"location"`
	Company     string    `json:"company"`
	ViewCount   int64     `json:"view_count"`
	CreatedAt   string    `json:"created_at"`
}

func ProfileFromModel(m model.UserProfile) UserProfileResponse {
	resp := UserProfileResponse{
		ID:        m.ID,
		UserID:    m.UserID,
		ViewCount: m.ViewCount,
		CreatedAt: m.CreatedAt.Format("2006-01-02T15:04:05Z"),
	}
	if m.DisplayName.Valid {
		resp.DisplayName = m.DisplayName.String
	}
	if m.AvatarUrl.Valid {
		resp.AvatarUrl = m.AvatarUrl.String
	}
	if m.Bio.Valid {
		resp.Bio = m.Bio.String
	}
	if m.Website.Valid {
		resp.Website = m.Website.String
	}
	if m.Location.Valid {
		resp.Location = m.Location.String
	}
	if m.Company.Valid {
		resp.Company = m.Company.String
	}
	return resp
}

type UserListResponse struct {
	Users []UserProfileResponse `json:"users"`
	Total int64                 `json:"total"`
}

type FollowResponse struct {
	Following bool   `json:"following"`
	CreatedAt string `json:"created_at,omitempty"`
}

type StatsResponse struct {
	PostCount      int `json:"post_count"`
	FollowerCount  int `json:"follower_count"`
	FollowingCount int `json:"following_count"`
	LikeCount      int `json:"like_count"`
}
