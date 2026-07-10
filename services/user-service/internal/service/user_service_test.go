package service

import (
	"database/sql"
	"errors"
	"testing"
	"time"

	"wenxinblog/user-service/internal/dto"
	"wenxinblog/user-service/internal/model"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// MockProfileRepository
type MockProfileRepository struct {
	GetByUserIDFunc  func(userID uuid.UUID) (*model.UserProfile, error)
	UpdateFunc       func(userID uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error
	SearchFunc       func(query string, limit, offset int) ([]model.UserProfile, int64, error)
	IncrementViewCountFunc func(userID uuid.UUID) error
}

func (m *MockProfileRepository) GetByUserID(userID uuid.UUID) (*model.UserProfile, error) {
	if m.GetByUserIDFunc != nil {
		return m.GetByUserIDFunc(userID)
	}
	return nil, nil
}

func (m *MockProfileRepository) Update(userID uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error {
	if m.UpdateFunc != nil {
		return m.UpdateFunc(userID, displayName, avatarUrl, bio, website, location, company, birthday)
	}
	return nil
}

func (m *MockProfileRepository) Search(query string, limit, offset int) ([]model.UserProfile, int64, error) {
	if m.SearchFunc != nil {
		return m.SearchFunc(query, limit, offset)
	}
	return []model.UserProfile{}, 0, nil
}

func (m *MockProfileRepository) IncrementViewCount(userID uuid.UUID) error {
	if m.IncrementViewCountFunc != nil {
		return m.IncrementViewCountFunc(userID)
	}
	return nil
}

func (m *MockProfileRepository) Create(profile *model.UserProfile) error { return nil }
func (m *MockProfileRepository) GetByID(id uuid.UUID) (*model.UserProfile, error) { return nil, nil }

// MockFollowRepository
type MockFollowRepository struct {
	FollowFunc        func(followerID, followingID uuid.UUID) error
	UnfollowFunc      func(followerID, followingID uuid.UUID) error
	IsFollowingFunc   func(followerID, followingID uuid.UUID) (bool, error)
	GetFollowersFunc  func(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error)
	GetFollowingFunc  func(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error)
	GetFollowingIDsFunc func(userID uuid.UUID) ([]uuid.UUID, error)
}

func (m *MockFollowRepository) Follow(followerID, followingID uuid.UUID) error {
	if m.FollowFunc != nil {
		return m.FollowFunc(followerID, followingID)
	}
	return nil
}

func (m *MockFollowRepository) Unfollow(followerID, followingID uuid.UUID) error {
	if m.UnfollowFunc != nil {
		return m.UnfollowFunc(followerID, followingID)
	}
	return nil
}

func (m *MockFollowRepository) IsFollowing(followerID, followingID uuid.UUID) (bool, error) {
	if m.IsFollowingFunc != nil {
		return m.IsFollowingFunc(followerID, followingID)
	}
	return false, nil
}

func (m *MockFollowRepository) GetFollowers(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error) {
	if m.GetFollowersFunc != nil {
		return m.GetFollowersFunc(userID, limit, offset)
	}
	return []model.UserProfile{}, 0, nil
}

func (m *MockFollowRepository) GetFollowing(userID uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error) {
	if m.GetFollowingFunc != nil {
		return m.GetFollowingFunc(userID, limit, offset)
	}
	return []model.UserProfile{}, 0, nil
}

func (m *MockFollowRepository) GetFollowingIDs(userID uuid.UUID) ([]uuid.UUID, error) {
	if m.GetFollowingIDsFunc != nil {
		return m.GetFollowingIDsFunc(userID)
	}
	return []uuid.UUID{}, nil
}

// MockStatsRepository
type MockStatsRepository struct {
	GetStatsFunc                func(userID uuid.UUID) (*model.UserStats, error)
	IncrementFollowerCountFunc  func(userID uuid.UUID) error
	DecrementFollowerCountFunc  func(userID uuid.UUID) error
	IncrementFollowingCountFunc func(userID uuid.UUID) error
	DecrementFollowingCountFunc func(userID uuid.UUID) error
}

func (m *MockStatsRepository) GetStats(userID uuid.UUID) (*model.UserStats, error) {
	if m.GetStatsFunc != nil {
		return m.GetStatsFunc(userID)
	}
	return &model.UserStats{}, nil
}

func (m *MockStatsRepository) UpsertStats(userID uuid.UUID, postCount, followerCount, followingCount, likeCount int) error {
	return nil
}

func (m *MockStatsRepository) IncrementFollowerCount(userID uuid.UUID) error {
	if m.IncrementFollowerCountFunc != nil {
		return m.IncrementFollowerCountFunc(userID)
	}
	return nil
}

func (m *MockStatsRepository) DecrementFollowerCount(userID uuid.UUID) error {
	if m.DecrementFollowerCountFunc != nil {
		return m.DecrementFollowerCountFunc(userID)
	}
	return nil
}

func (m *MockStatsRepository) IncrementFollowingCount(userID uuid.UUID) error {
	if m.IncrementFollowingCountFunc != nil {
		return m.IncrementFollowingCountFunc(userID)
	}
	return nil
}

func (m *MockStatsRepository) DecrementFollowingCount(userID uuid.UUID) error {
	if m.DecrementFollowingCountFunc != nil {
		return m.DecrementFollowingCountFunc(userID)
	}
	return nil
}

func TestUserService_GetProfile_Success(t *testing.T) {
	userID := uuid.New()
	expectedProfile := &model.UserProfile{
		ID:     uuid.New(),
		UserID: userID,
		DisplayName: sql.NullString{String: "Test User", Valid: true},
		Bio:         sql.NullString{String: "Test bio", Valid: true},
		ViewCount:   100,
		CreatedAt:   time.Now(),
	}

	mockProfile := &MockProfileRepository{
		GetByUserIDFunc: func(id uuid.UUID) (*model.UserProfile, error) {
			return expectedProfile, nil
		},
		IncrementViewCountFunc: func(id uuid.UUID) error { return nil },
	}

	svc := NewUserService(mockProfile, nil, nil)
	profile, err := svc.GetProfile(userID)

	require.NoError(t, err)
	require.NotNil(t, profile)
	assert.Equal(t, "Test User", profile.DisplayName)
	assert.Equal(t, "Test bio", profile.Bio)
}

func TestUserService_GetProfile_NotFound(t *testing.T) {
	userID := uuid.New()

	mockProfile := &MockProfileRepository{
		GetByUserIDFunc: func(id uuid.UUID) (*model.UserProfile, error) {
			return nil, nil
		},
	}

	svc := NewUserService(mockProfile, nil, nil)
	profile, err := svc.GetProfile(userID)

	require.NoError(t, err)
	assert.Nil(t, profile)
}

func TestUserService_GetProfile_Error(t *testing.T) {
	userID := uuid.New()

	mockProfile := &MockProfileRepository{
		GetByUserIDFunc: func(id uuid.UUID) (*model.UserProfile, error) {
			return nil, errors.New("database error")
		},
	}

	svc := NewUserService(mockProfile, nil, nil)
	profile, err := svc.GetProfile(userID)

	assert.Error(t, err)
	assert.Nil(t, profile)
}

func TestUserService_UpdateProfile_Success(t *testing.T) {
	userID := uuid.New()
	updatedProfile := &model.UserProfile{
		ID:     uuid.New(),
		UserID: userID,
		DisplayName: sql.NullString{String: "Updated Name", Valid: true},
	}

	mockProfile := &MockProfileRepository{
		UpdateFunc: func(id uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error {
			return nil
		},
		GetByUserIDFunc: func(id uuid.UUID) (*model.UserProfile, error) {
			return updatedProfile, nil
		},
	}

	svc := NewUserService(mockProfile, nil, nil)
	displayName := "Updated Name"
	req := &dto.UpdateProfileRequest{
		DisplayName: &displayName,
	}

	profile, err := svc.UpdateProfile(userID, req)

	require.NoError(t, err)
	require.NotNil(t, profile)
}

func TestUserService_UpdateProfile_InvalidBirthday(t *testing.T) {
	userID := uuid.New()
	invalidDate := "not-a-date"

	mockProfile := &MockProfileRepository{}

	svc := NewUserService(mockProfile, nil, nil)
	req := &dto.UpdateProfileRequest{
		Birthday: &invalidDate,
	}

	_, err := svc.UpdateProfile(userID, req)
	assert.Error(t, err)
}

func TestUserService_UpdateProfile_DBError(t *testing.T) {
	userID := uuid.New()

	mockProfile := &MockProfileRepository{
		UpdateFunc: func(id uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error {
			return errors.New("update failed")
		},
	}

	svc := NewUserService(mockProfile, nil, nil)
	displayName := "Test"
	req := &dto.UpdateProfileRequest{
		DisplayName: &displayName,
	}

	_, err := svc.UpdateProfile(userID, req)
	assert.Error(t, err)
}

func TestUserService_SearchUsers_Success(t *testing.T) {
	profiles := []model.UserProfile{
		{ID: uuid.New(), DisplayName: sql.NullString{String: "User One", Valid: true}},
		{ID: uuid.New(), DisplayName: sql.NullString{String: "User Two", Valid: true}},
	}

	mockProfile := &MockProfileRepository{
		SearchFunc: func(query string, limit, offset int) ([]model.UserProfile, int64, error) {
			return profiles, int64(len(profiles)), nil
		},
	}

	svc := NewUserService(mockProfile, nil, nil)
	result, err := svc.SearchUsers("test", 1, 10)

	require.NoError(t, err)
	assert.Equal(t, 2, len(result.Users))
	assert.Equal(t, int64(2), result.Total)
}

func TestUserService_FollowUser_Success(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockFollow := &MockFollowRepository{
		FollowFunc: func(fid, tid uuid.UUID) error {
			return nil
		},
	}

	mockStats := &MockStatsRepository{
		IncrementFollowerCountFunc: func(id uuid.UUID) error { return nil },
		IncrementFollowingCountFunc: func(id uuid.UUID) error { return nil },
	}

	svc := NewUserService(nil, mockFollow, mockStats)
	err := svc.FollowUser(followerID, followingID)

	require.NoError(t, err)
}

func TestUserService_FollowUser_SelfFollow(t *testing.T) {
	userID := uuid.New()

	mockFollow := &MockFollowRepository{}

	svc := NewUserService(nil, mockFollow, nil)
	err := svc.FollowUser(userID, userID)

	require.NoError(t, err) // Should not error, just return early
}

func TestUserService_FollowUser_Error(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockFollow := &MockFollowRepository{
		FollowFunc: func(fid, tid uuid.UUID) error {
			return errors.New("follow failed")
		},
	}

	svc := NewUserService(nil, mockFollow, nil)
	err := svc.FollowUser(followerID, followingID)

	assert.Error(t, err)
}

func TestUserService_UnfollowUser_Success(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockFollow := &MockFollowRepository{
		UnfollowFunc: func(fid, tid uuid.UUID) error {
			return nil
		},
	}

	mockStats := &MockStatsRepository{
		DecrementFollowerCountFunc: func(id uuid.UUID) error { return nil },
		DecrementFollowingCountFunc: func(id uuid.UUID) error { return nil },
	}

	svc := NewUserService(nil, mockFollow, mockStats)
	err := svc.UnfollowUser(followerID, followingID)

	require.NoError(t, err)
}

func TestUserService_GetFollowers_Success(t *testing.T) {
	userID := uuid.New()
	profiles := []model.UserProfile{
		{ID: uuid.New(), DisplayName: sql.NullString{String: "Follower One", Valid: true}},
	}

	mockFollow := &MockFollowRepository{
		GetFollowersFunc: func(uid uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error) {
			return profiles, int64(len(profiles)), nil
		},
	}

	svc := NewUserService(nil, mockFollow, nil)
	result, err := svc.GetFollowers(userID, 1, 10)

	require.NoError(t, err)
	assert.Equal(t, 1, len(result.Users))
	assert.Equal(t, int64(1), result.Total)
}

func TestUserService_GetFollowing_Success(t *testing.T) {
	userID := uuid.New()
	profiles := []model.UserProfile{
		{ID: uuid.New(), DisplayName: sql.NullString{String: "Following One", Valid: true}},
	}

	mockFollow := &MockFollowRepository{
		GetFollowingFunc: func(uid uuid.UUID, limit, offset int) ([]model.UserProfile, int64, error) {
			return profiles, int64(len(profiles)), nil
		},
	}

	svc := NewUserService(nil, mockFollow, nil)
	result, err := svc.GetFollowing(userID, 1, 10)

	require.NoError(t, err)
	assert.Equal(t, 1, len(result.Users))
}

func TestUserService_IsFollowing_Success(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockFollow := &MockFollowRepository{
		IsFollowingFunc: func(fid, tid uuid.UUID) (bool, error) {
			return true, nil
		},
	}

	svc := NewUserService(nil, mockFollow, nil)
	following, err := svc.IsFollowing(followerID, followingID)

	require.NoError(t, err)
	assert.True(t, following)
}

func TestUserService_GetFollowingIDs_Success(t *testing.T) {
	userID := uuid.New()
	ids := []uuid.UUID{uuid.New(), uuid.New()}

	mockFollow := &MockFollowRepository{
		GetFollowingIDsFunc: func(uid uuid.UUID) ([]uuid.UUID, error) {
			return ids, nil
		},
	}

	svc := NewUserService(nil, mockFollow, nil)
	result, err := svc.GetFollowingIDs(userID)

	require.NoError(t, err)
	assert.Equal(t, 2, len(result))
}

func TestUserService_GetStats_Success(t *testing.T) {
	userID := uuid.New()

	mockStats := &MockStatsRepository{
		GetStatsFunc: func(uid uuid.UUID) (*model.UserStats, error) {
			return &model.UserStats{
				UserID:         uid,
				PostCount:      10,
				FollowerCount:  100,
				FollowingCount: 50,
				LikeCount:      200,
			}, nil
		},
	}

	svc := NewUserService(nil, nil, mockStats)
	stats, err := svc.GetStats(userID)

	require.NoError(t, err)
	assert.Equal(t, 10, stats.PostCount)
	assert.Equal(t, 100, stats.FollowerCount)
	assert.Equal(t, 50, stats.FollowingCount)
	assert.Equal(t, 200, stats.LikeCount)
}

func TestUserService_GetStats_Error(t *testing.T) {
	userID := uuid.New()

	mockStats := &MockStatsRepository{
		GetStatsFunc: func(uid uuid.UUID) (*model.UserStats, error) {
			return nil, errors.New("stats error")
		},
	}

	svc := NewUserService(nil, nil, mockStats)
	_, err := svc.GetStats(userID)

	assert.Error(t, err)
}

func TestUserService_Search_EmptyResult(t *testing.T) {
	mockProfile := &MockProfileRepository{
		SearchFunc: func(query string, limit, offset int) ([]model.UserProfile, int64, error) {
			return []model.UserProfile{}, 0, nil
		},
	}

	svc := NewUserService(mockProfile, nil, nil)
	result, err := svc.SearchUsers("nonexistent", 1, 10)

	require.NoError(t, err)
	assert.Equal(t, 0, len(result.Users))
	assert.Equal(t, int64(0), result.Total)
}

func TestUserService_UpdateProfile_NullValues(t *testing.T) {
	userID := uuid.New()
	updatedProfile := &model.UserProfile{
		ID:     uuid.New(),
		UserID: userID,
		DisplayName: sql.NullString{String: "Test", Valid: true},
	}

	mockProfile := &MockProfileRepository{
		UpdateFunc: func(id uuid.UUID, displayName, avatarUrl, bio, website, location, company *string, birthday *time.Time) error {
			return nil
		},
		GetByUserIDFunc: func(id uuid.UUID) (*model.UserProfile, error) {
			return updatedProfile, nil
		},
	}

	svc := NewUserService(mockProfile, nil, nil)

	// Test with nil values
	req := &dto.UpdateProfileRequest{}
	profile, err := svc.UpdateProfile(userID, req)

	require.NoError(t, err)
	require.NotNil(t, profile)
}
