package dto

import (
	"database/sql"
	"testing"
	"time"

	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func TestDTO_Success(t *testing.T) {
	data := map[string]string{"key": "value"}
	result := Success(data)

	assert.Equal(t, 0, result.Code)
	assert.Equal(t, "ok", result.Message)
	assert.Equal(t, data, result.Data)
}

func TestDTO_Error(t *testing.T) {
	errMsg := "something went wrong"
	result := Error(errMsg)

	assert.Equal(t, -1, result.Code)
	assert.Equal(t, errMsg, result.Message)
}

func TestDTO_ProfileFromModel_FullProfile(t *testing.T) {
	userID := uuid.New()
	profileID := uuid.New()

	m := model.UserProfile{
		ID:        profileID,
		UserID:    userID,
		DisplayName: sql.NullString{String: "Test User", Valid: true},
		AvatarUrl:   sql.NullString{String: "http://example.com/avatar.jpg", Valid: true},
		Bio:         sql.NullString{String: "Test bio", Valid: true},
		Website:     sql.NullString{String: "http://example.com", Valid: true},
		Location:    sql.NullString{String: "New York", Valid: true},
		Company:     sql.NullString{String: "Test Company", Valid: true},
		Birthday:    sql.NullTime{Time: time.Date(1990, 1, 1, 0, 0, 0, 0, time.UTC), Valid: true},
		ViewCount:   1000,
		CreatedAt:   time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC),
	}

	result := ProfileFromModel(m)

	assert.Equal(t, profileID, result.ID)
	assert.Equal(t, userID, result.UserID)
	assert.Equal(t, "Test User", result.DisplayName)
	assert.Equal(t, "http://example.com/avatar.jpg", result.AvatarUrl)
	assert.Equal(t, "Test bio", result.Bio)
	assert.Equal(t, "http://example.com", result.Website)
	assert.Equal(t, "New York", result.Location)
	assert.Equal(t, "Test Company", result.Company)
	assert.Equal(t, int64(1000), result.ViewCount)
	assert.Equal(t, "2024-01-01T00:00:00Z", result.CreatedAt)
}

func TestDTO_ProfileFromModel_NullFields(t *testing.T) {
	userID := uuid.New()
	profileID := uuid.New()

	m := model.UserProfile{
		ID:        profileID,
		UserID:    userID,
		DisplayName: sql.NullString{Valid: false},
		AvatarUrl:   sql.NullString{Valid: false},
		Bio:         sql.NullString{Valid: false},
		Website:     sql.NullString{Valid: false},
		Location:    sql.NullString{Valid: false},
		Company:     sql.NullString{Valid: false},
		Birthday:    sql.NullTime{Valid: false},
		ViewCount:   0,
		CreatedAt:   time.Now(),
	}

	result := ProfileFromModel(m)

	assert.Equal(t, profileID, result.ID)
	assert.Equal(t, userID, result.UserID)
	assert.Empty(t, result.DisplayName) // Should be empty string for NULL
	assert.Empty(t, result.AvatarUrl)
	assert.Empty(t, result.Bio)
	assert.Empty(t, result.Website)
	assert.Empty(t, result.Location)
	assert.Empty(t, result.Company)
	assert.Equal(t, int64(0), result.ViewCount)
}

func TestDTO_ProfileFromModel_PartialFields(t *testing.T) {
	userID := uuid.New()
	profileID := uuid.New()

	m := model.UserProfile{
		ID:        profileID,
		UserID:    userID,
		DisplayName: sql.NullString{String: "Partial User", Valid: true},
		Bio:         sql.NullString{String: "Has bio", Valid: true},
		// Other fields are NULL
		AvatarUrl:   sql.NullString{Valid: false},
		Website:     sql.NullString{Valid: false},
		Location:    sql.NullString{Valid: false},
		Company:     sql.NullString{Valid: false},
		Birthday:    sql.NullTime{Valid: false},
		ViewCount:   50,
		CreatedAt:   time.Now(),
	}

	result := ProfileFromModel(m)

	assert.Equal(t, "Partial User", result.DisplayName)
	assert.Equal(t, "Has bio", result.Bio)
	assert.Empty(t, result.AvatarUrl)
	assert.Empty(t, result.Website)
	assert.Empty(t, result.Location)
	assert.Empty(t, result.Company)
	assert.Equal(t, int64(50), result.ViewCount)
}

func TestDTO_FollowResponse(t *testing.T) {
	resp := FollowResponse{
		Following: true,
		CreatedAt: "2024-01-01T00:00:00Z",
	}

	assert.True(t, resp.Following)
	assert.Equal(t, "2024-01-01T00:00:00Z", resp.CreatedAt)
}

func TestDTO_StatsResponse(t *testing.T) {
	resp := StatsResponse{
		PostCount:      100,
		FollowerCount:  500,
		FollowingCount: 250,
		LikeCount:      1000,
	}

	assert.Equal(t, 100, resp.PostCount)
	assert.Equal(t, 500, resp.FollowerCount)
	assert.Equal(t, 250, resp.FollowingCount)
	assert.Equal(t, 1000, resp.LikeCount)
}

func TestDTO_UserListResponse(t *testing.T) {
	users := []UserProfileResponse{
		{ID: uuid.New(), DisplayName: "User One"},
		{ID: uuid.New(), DisplayName: "User Two"},
	}

	resp := UserListResponse{
		Users: users,
		Total: 2,
	}

	assert.Equal(t, 2, len(resp.Users))
	assert.Equal(t, int64(2), resp.Total)
}

func TestDTO_UpdateProfileRequest_PointerFields(t *testing.T) {
	name := "Updated Name"
	bio := "Updated bio"

	req := UpdateProfileRequest{
		DisplayName: &name,
		Bio:         &bio,
		AvatarUrl:   nil, // Not updating
		Website:     nil,
	}

	assert.Equal(t, "Updated Name", *req.DisplayName)
	assert.Equal(t, "Updated bio", *req.Bio)
	assert.Nil(t, req.AvatarUrl)
	assert.Nil(t, req.Website)
}

func TestDTO_APIResponse_DataOptional(t *testing.T) {
	// With data
	resp1 := APIResponse{
		Code:    0,
		Message: "ok",
		Data:    map[string]string{"key": "value"},
	}
	assert.NotNil(t, resp1.Data)

	// Without data
	resp2 := APIResponse{
		Code:    -1,
		Message: "error",
	}
	assert.Nil(t, resp2.Data)
}

func TestDTO_ProfileFromModel_TimestampFormat(t *testing.T) {
	m := model.UserProfile{
		CreatedAt: time.Date(2024, 3, 15, 14, 30, 0, 0, time.UTC),
	}

	result := ProfileFromModel(m)

	// Verify ISO 8601 format
	assert.Equal(t, "2024-03-15T14:30:00Z", result.CreatedAt)
}
