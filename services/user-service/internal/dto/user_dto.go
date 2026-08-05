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

type InternalCreateUserRequest struct {
	ID       string `json:"id"`
	Username string `json:"username"`
	Email    string `json:"email"`
}

// UpdateProfileRequest 字段对齐前端（camelCase，avatar 非 avatarUrl）。
type UpdateProfileRequest struct {
	DisplayName *string `json:"displayName"`
	Avatar      *string `json:"avatar"`
	Bio         *string `json:"bio"`
	Website     *string `json:"website"`
	Location    *string `json:"location"`
	Company     *string `json:"company"`
	Birthday    *string `json:"birthday"`
}

// UserProfileResponse 对齐前端 UserProfile：camelCase + username/email/统计/isFollowing。
// username/email/统计/isFollowing 由 service 查库补填，ProfileFromModel 只填 profile 基础字段。
type UserProfileResponse struct {
	ID             uuid.UUID `json:"id"`
	UserID         uuid.UUID `json:"userId"`
	Username       string    `json:"username"`
	Email          string    `json:"email,omitempty"`
	DisplayName    string    `json:"displayName"`
	Avatar         string    `json:"avatar"`
	Bio            string    `json:"bio"`
	Website        string    `json:"website"`
	Location       string    `json:"location"`
	Company        string    `json:"company"`
	ViewCount      int64     `json:"viewCount"`
	FollowersCount int       `json:"followersCount"`
	FollowingCount int       `json:"followingCount"`
	PostsCount     int       `json:"postsCount"`
	IsFollowing    bool      `json:"isFollowing"`
	CreatedAt      string    `json:"createdAt"`
}

// ProfileFromModel 只填 profile 基础字段；username/email/统计/isFollowing 由 service.enrichProfile 补。
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
		resp.Avatar = m.AvatarUrl.String
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

// UserListResponse 对齐前端 PaginatedResponse（items/total/page/pageSize/totalPages）。
type UserListResponse struct {
	Items      []UserProfileResponse `json:"items"`
	Total      int64                 `json:"total"`
	Page       int                   `json:"page"`
	PageSize   int                   `json:"pageSize"`
	TotalPages int                   `json:"totalPages"`
}

type FollowResponse struct {
	Following bool   `json:"following"`
	CreatedAt string `json:"createdAt,omitempty"`
}

type StatsResponse struct {
	PostCount      int `json:"postCount"`
	FollowerCount  int `json:"followerCount"`
	FollowingCount int `json:"followingCount"`
	LikeCount      int `json:"likeCount"`
}
