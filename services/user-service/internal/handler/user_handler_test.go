package handler

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"testing"

	"wenxinblog/user-service/internal/dto"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// MockUserService implements service.UserServicer for testing
type MockUserService struct {
	getProfileFunc     func(userID uuid.UUID) (*dto.UserProfileResponse, error)
	updateProfileFunc  func(userID uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error)
	getStatsFunc       func(userID uuid.UUID) (*dto.StatsResponse, error)
	getFollowersFunc   func(userID uuid.UUID, page, size int) (*dto.UserListResponse, error)
	getFollowingFunc   func(userID uuid.UUID, page, size int) (*dto.UserListResponse, error)
	followUserFunc     func(followerID, followingID uuid.UUID) error
	unfollowUserFunc   func(followerID, followingID uuid.UUID) error
	searchUsersFunc    func(query string, page, size int) (*dto.UserListResponse, error)
	getFollowingIDsFunc func(userID uuid.UUID) ([]uuid.UUID, error)
}

func (m *MockUserService) GetProfile(userID uuid.UUID) (*dto.UserProfileResponse, error) {
	if m.getProfileFunc != nil {
		return m.getProfileFunc(userID)
	}
	return nil, nil
}

func (m *MockUserService) UpdateProfile(userID uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error) {
	if m.updateProfileFunc != nil {
		return m.updateProfileFunc(userID, req)
	}
	return nil, nil
}

func (m *MockUserService) GetStats(userID uuid.UUID) (*dto.StatsResponse, error) {
	if m.getStatsFunc != nil {
		return m.getStatsFunc(userID)
	}
	return nil, nil
}

func (m *MockUserService) GetFollowers(userID uuid.UUID, page, size int) (*dto.UserListResponse, error) {
	if m.getFollowersFunc != nil {
		return m.getFollowersFunc(userID, page, size)
	}
	return nil, nil
}

func (m *MockUserService) GetFollowing(userID uuid.UUID, page, size int) (*dto.UserListResponse, error) {
	if m.getFollowingFunc != nil {
		return m.getFollowingFunc(userID, page, size)
	}
	return nil, nil
}

func (m *MockUserService) FollowUser(followerID, followingID uuid.UUID) error {
	if m.followUserFunc != nil {
		return m.followUserFunc(followerID, followingID)
	}
	return nil
}

func (m *MockUserService) UnfollowUser(followerID, followingID uuid.UUID) error {
	if m.unfollowUserFunc != nil {
		return m.unfollowUserFunc(followerID, followingID)
	}
	return nil
}

func (m *MockUserService) SearchUsers(query string, page, size int) (*dto.UserListResponse, error) {
	if m.searchUsersFunc != nil {
		return m.searchUsersFunc(query, page, size)
	}
	return nil, nil
}

func (m *MockUserService) GetFollowingIDs(userID uuid.UUID) ([]uuid.UUID, error) {
	if m.getFollowingIDsFunc != nil {
		return m.getFollowingIDsFunc(userID)
	}
	return nil, nil
}

func (m *MockUserService) IsFollowing(followerID, followingID uuid.UUID) (bool, error) {
	return false, nil
}

// Helper function to setup test app with handler
func setupTestApp(handler *UserHandler, route string, handlerFunc fiber.Handler) *fiber.App {
	app := fiber.New()
	app.Post(route, handlerFunc)
	app.Get(route, handlerFunc)
	app.Put(route, handlerFunc)
	app.Delete(route, handlerFunc)
	return app
}

func TestHandler_GetProfile_Success(t *testing.T) {
	userID := uuid.New()
	expectedProfile := &dto.UserProfileResponse{
		ID:          userID,
		UserID:      userID,
		DisplayName: "Test User",
		Bio:         "Test bio",
	}

	mockSvc := &MockUserService{
		getProfileFunc: func(id uuid.UUID) (*dto.UserProfileResponse, error) {
			if id == userID {
				return expectedProfile, nil
			}
			return nil, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id", handler.GetProfile)

	req := httptest.NewRequest("GET", "/"+userID.String(), nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)

	var result dto.APIResponse
	json.NewDecoder(resp.Body).Decode(&result)
	assert.Equal(t, 0, result.Code)
}

func TestHandler_GetProfile_InvalidID(t *testing.T) {
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id", handler.GetProfile)

	req := httptest.NewRequest("GET", "/invalid-uuid", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_GetProfile_NotFound(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getProfileFunc: func(id uuid.UUID) (*dto.UserProfileResponse, error) {
			return nil, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id", handler.GetProfile)

	req := httptest.NewRequest("GET", "/"+userID.String(), nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 404, resp.StatusCode)
}

func TestHandler_GetProfile_ServiceError(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getProfileFunc: func(id uuid.UUID) (*dto.UserProfileResponse, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id", handler.GetProfile)

	req := httptest.NewRequest("GET", "/"+userID.String(), nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_UpdateProfile_Success(t *testing.T) {
	userID := uuid.New()
	updatedProfile := &dto.UserProfileResponse{
		ID:          userID,
		UserID:      userID,
		DisplayName: "Updated",
	}

	mockSvc := &MockUserService{
		updateProfileFunc: func(id uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error) {
			return updatedProfile, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Put("/:id", handler.UpdateProfile)

	displayName := "Updated"
	body, _ := json.Marshal(map[string]*string{
		"display_name": &displayName,
	})
	req := httptest.NewRequest("PUT", "/"+userID.String(), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_UpdateProfile_InvalidID(t *testing.T) {
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Put("/:id", handler.UpdateProfile)

	displayName := "Updated"
	body, _ := json.Marshal(map[string]*string{
		"display_name": &displayName,
	})
	req := httptest.NewRequest("PUT", "/invalid-uuid", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_UpdateProfile_InvalidBody(t *testing.T) {
	userID := uuid.New()
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Put("/:id", handler.UpdateProfile)

	req := httptest.NewRequest("PUT", "/"+userID.String(), bytes.NewReader([]byte("invalid json")))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_UpdateProfile_ServiceError(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		updateProfileFunc: func(id uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Put("/:id", handler.UpdateProfile)

	displayName := "Updated"
	body, _ := json.Marshal(map[string]*string{
		"display_name": &displayName,
	})
	req := httptest.NewRequest("PUT", "/"+userID.String(), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_UpdateProfile_WithBirthday(t *testing.T) {
	userID := uuid.New()
	updatedProfile := &dto.UserProfileResponse{
		ID:          userID,
		UserID:      userID,
		DisplayName: "Test User",
	}

	mockSvc := &MockUserService{
		updateProfileFunc: func(id uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error) {
			return updatedProfile, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Put("/:id", handler.UpdateProfile)

	birthday := "1990-01-01"
	body, _ := json.Marshal(map[string]*string{
		"birthday": &birthday,
	})
	req := httptest.NewRequest("PUT", "/"+userID.String(), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_GetStats_Success(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getStatsFunc: func(id uuid.UUID) (*dto.StatsResponse, error) {
			return &dto.StatsResponse{
				PostCount:      10,
				FollowerCount:  100,
				FollowingCount: 50,
				LikeCount:      200,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/stats", func(c *fiber.Ctx) error {
		c.Params("id", userID.String())
		return handler.GetStats(c)
	})

	req := httptest.NewRequest("GET", "/"+userID.String()+"/stats", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_GetStats_InvalidID(t *testing.T) {
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/stats", handler.GetStats)

	req := httptest.NewRequest("GET", "/invalid-uuid/stats", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_GetStats_ServiceError(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getStatsFunc: func(id uuid.UUID) (*dto.StatsResponse, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/stats", handler.GetStats)

	req := httptest.NewRequest("GET", "/"+userID.String()+"/stats", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_GetFollowers_Success(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getFollowersFunc: func(id uuid.UUID, page, size int) (*dto.UserListResponse, error) {
			return &dto.UserListResponse{
				Users: []dto.UserProfileResponse{},
				Total: 0,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/followers", handler.GetFollowers)

	req := httptest.NewRequest("GET", "/"+userID.String()+"/followers?page=1&size=20", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_GetFollowers_InvalidID(t *testing.T) {
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/followers", handler.GetFollowers)

	req := httptest.NewRequest("GET", "/invalid-uuid/followers", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_GetFollowers_ServiceError(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getFollowersFunc: func(id uuid.UUID, page, size int) (*dto.UserListResponse, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/followers", handler.GetFollowers)

	req := httptest.NewRequest("GET", "/"+userID.String()+"/followers", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_GetFollowers_PaginationDefaults(t *testing.T) {
	userID := uuid.New()
	capturedPage, capturedSize := 0, 0

	mockSvc := &MockUserService{
		getFollowersFunc: func(id uuid.UUID, page, size int) (*dto.UserListResponse, error) {
			capturedPage = page
			capturedSize = size
			return &dto.UserListResponse{
				Users: []dto.UserProfileResponse{},
				Total: 0,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/followers", handler.GetFollowers)

	req := httptest.NewRequest("GET", "/"+userID.String()+"/followers", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
	assert.Equal(t, 1, capturedPage)
	assert.Equal(t, 20, capturedSize)
}

func TestHandler_GetFollowing_Success(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getFollowingFunc: func(id uuid.UUID, page, size int) (*dto.UserListResponse, error) {
			return &dto.UserListResponse{
				Users: []dto.UserProfileResponse{},
				Total: 0,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/following", handler.GetFollowing)

	req := httptest.NewRequest("GET", "/"+userID.String()+"/following", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_GetFollowing_InvalidID(t *testing.T) {
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/following", handler.GetFollowing)

	req := httptest.NewRequest("GET", "/invalid-uuid/following", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_GetFollowing_ServiceError(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getFollowingFunc: func(id uuid.UUID, page, size int) (*dto.UserListResponse, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/:id/following", handler.GetFollowing)

	req := httptest.NewRequest("GET", "/"+userID.String()+"/following", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_FollowUser_Success(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockSvc := &MockUserService{
		followUserFunc: func(fID, fuID uuid.UUID) error {
			return nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Post("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID.String())
		return handler.FollowUser(c)
	})

	req := httptest.NewRequest("POST", "/"+followingID.String()+"/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_FollowUser_InvalidUserID(t *testing.T) {
	followerID := "not-a-uuid"
	followingID := uuid.New()

	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Post("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID)
		return handler.FollowUser(c)
	})

	req := httptest.NewRequest("POST", "/"+followingID.String()+"/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_FollowUser_InvalidFollowingID(t *testing.T) {
	followerID := uuid.New()

	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Post("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID.String())
		return handler.FollowUser(c)
	})

	req := httptest.NewRequest("POST", "/invalid-uuid/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_FollowUser_ServiceError(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockSvc := &MockUserService{
		followUserFunc: func(fID, fuID uuid.UUID) error {
			return assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Post("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID.String())
		return handler.FollowUser(c)
	})

	req := httptest.NewRequest("POST", "/"+followingID.String()+"/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_UnfollowUser_Success(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockSvc := &MockUserService{
		unfollowUserFunc: func(fID, fuID uuid.UUID) error {
			return nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Delete("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID.String())
		return handler.UnfollowUser(c)
	})

	req := httptest.NewRequest("DELETE", "/"+followingID.String()+"/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_UnfollowUser_InvalidUserID(t *testing.T) {
	followerID := "not-a-uuid"
	followingID := uuid.New()

	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Delete("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID)
		return handler.UnfollowUser(c)
	})

	req := httptest.NewRequest("DELETE", "/"+followingID.String()+"/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_UnfollowUser_ServiceError(t *testing.T) {
	followerID := uuid.New()
	followingID := uuid.New()

	mockSvc := &MockUserService{
		unfollowUserFunc: func(fID, fuID uuid.UUID) error {
			return assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Delete("/:id/follow", func(c *fiber.Ctx) error {
		c.Locals("userID", followerID.String())
		return handler.UnfollowUser(c)
	})

	req := httptest.NewRequest("DELETE", "/"+followingID.String()+"/follow", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_SearchUsers_Success(t *testing.T) {
	mockSvc := &MockUserService{
		searchUsersFunc: func(query string, page, size int) (*dto.UserListResponse, error) {
			return &dto.UserListResponse{
				Users: []dto.UserProfileResponse{},
				Total: 0,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/search", handler.SearchUsers)

	req := httptest.NewRequest("GET", "/search?q=test&page=1&size=20", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_SearchUsers_MissingQuery(t *testing.T) {
	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/search", handler.SearchUsers)

	req := httptest.NewRequest("GET", "/search", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_SearchUsers_ServiceError(t *testing.T) {
	mockSvc := &MockUserService{
		searchUsersFunc: func(query string, page, size int) (*dto.UserListResponse, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/search", handler.SearchUsers)

	req := httptest.NewRequest("GET", "/search?q=test", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_SearchUsers_WithPagination(t *testing.T) {
	capturedPage, capturedSize := 0, 0

	mockSvc := &MockUserService{
		searchUsersFunc: func(query string, page, size int) (*dto.UserListResponse, error) {
			capturedPage = page
			capturedSize = size
			return &dto.UserListResponse{
				Users: []dto.UserProfileResponse{},
				Total: 0,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/search", handler.SearchUsers)

	req := httptest.NewRequest("GET", "/search?q=test&page=2&size=50", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
	assert.Equal(t, 2, capturedPage)
	assert.Equal(t, 50, capturedSize)
}

func TestHandler_GetMyFollowing_Success(t *testing.T) {
	userID := uuid.New()
	ids := []uuid.UUID{uuid.New(), uuid.New()}

	mockSvc := &MockUserService{
		getFollowingIDsFunc: func(id uuid.UUID) ([]uuid.UUID, error) {
			return ids, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/me/following", func(c *fiber.Ctx) error {
		c.Locals("userID", userID.String())
		return handler.GetMyFollowing(c)
	})

	req := httptest.NewRequest("GET", "/me/following", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestHandler_GetMyFollowing_InvalidUserID(t *testing.T) {
	userID := "not-a-uuid"

	mockSvc := &MockUserService{}
	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/me/following", func(c *fiber.Ctx) error {
		c.Locals("userID", userID)
		return handler.GetMyFollowing(c)
	})

	req := httptest.NewRequest("GET", "/me/following", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestHandler_GetMyFollowing_ServiceError(t *testing.T) {
	userID := uuid.New()

	mockSvc := &MockUserService{
		getFollowingIDsFunc: func(id uuid.UUID) ([]uuid.UUID, error) {
			return nil, assert.AnError
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Get("/me/following", func(c *fiber.Ctx) error {
		c.Locals("userID", userID.String())
		return handler.GetMyFollowing(c)
	})

	req := httptest.NewRequest("GET", "/me/following", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestHandler_ParsePagination_Defaults(t *testing.T) {
	app := fiber.New()
	app.Get("/test", func(c *fiber.Ctx) error {
		page, size := parsePagination(c)
		return c.JSON(fiber.Map{"page": page, "size": size})
	})

	req := httptest.NewRequest("GET", "/test", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)

	var result map[string]int
	json.NewDecoder(resp.Body).Decode(&result)
	assert.Equal(t, 1, result["page"])
	assert.Equal(t, 20, result["size"])
}

func TestHandler_ParsePagination_CustomValues(t *testing.T) {
	app := fiber.New()
	app.Get("/test", func(c *fiber.Ctx) error {
		page, size := parsePagination(c)
		return c.JSON(fiber.Map{"page": page, "size": size})
	})

	req := httptest.NewRequest("GET", "/test?page=5&size=50", nil)
	resp, err := app.Test(req)
	require.NoError(t, err)

	var result map[string]int
	json.NewDecoder(resp.Body).Decode(&result)
	assert.Equal(t, 5, result["page"])
	assert.Equal(t, 50, result["size"])
}

func TestHandler_ParsePagination_BoundaryValues(t *testing.T) {
	tests := []struct {
		name           string
		pageParam      string
		sizeParam      string
		expectedPage   int
		expectedSize   int
	}{
		{"Page less than 1 defaults to 1", "0", "20", 1, 20},
		{"Size less than 1 defaults to 20", "1", "0", 1, 20},
		{"Size greater than 100 defaults to 20", "1", "101", 1, 20},
		{"Valid boundary values", "100", "100", 100, 100},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			app := fiber.New()
			app.Get("/test", func(c *fiber.Ctx) error {
				page, size := parsePagination(c)
				return c.JSON(fiber.Map{"page": page, "size": size})
			})

			req := httptest.NewRequest("GET", "/test?page="+tt.pageParam+"&size="+tt.sizeParam, nil)
			resp, err := app.Test(req)
			require.NoError(t, err)

			var result map[string]int
			json.NewDecoder(resp.Body).Decode(&result)
			assert.Equal(t, tt.expectedPage, result["page"])
			assert.Equal(t, tt.expectedSize, result["size"])
		})
	}
}

func TestHandler_Integration_CompleteFlow(t *testing.T) {
	userID := uuid.New()
	profiles := make(map[uuid.UUID]*dto.UserProfileResponse)

	mockSvc := &MockUserService{
		getProfileFunc: func(id uuid.UUID) (*dto.UserProfileResponse, error) {
			if profile, ok := profiles[id]; ok {
				return profile, nil
			}
			return nil, nil
		},
		updateProfileFunc: func(id uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error) {
			profile := &dto.UserProfileResponse{
				ID:          id,
				UserID:      id,
				DisplayName: "Test User",
			}
			if req.DisplayName != nil {
				profile.DisplayName = *req.DisplayName
			}
			profiles[id] = profile
			return profile, nil
		},
		getStatsFunc: func(id uuid.UUID) (*dto.StatsResponse, error) {
			return &dto.StatsResponse{
				PostCount:      5,
				FollowerCount:  25,
				FollowingCount: 10,
				LikeCount:      50,
			}, nil
		},
	}

	handler := NewUserHandler(mockSvc)
	app := fiber.New()
	app.Put("/:id", handler.UpdateProfile)
	app.Get("/:id", handler.GetProfile)
	app.Get("/:id/stats", handler.GetStats)

	// Update profile
	displayName := "Updated User"
	body, _ := json.Marshal(map[string]*string{
		"display_name": &displayName,
	})
	req := httptest.NewRequest("PUT", "/"+userID.String(), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)

	// Get profile
	req = httptest.NewRequest("GET", "/"+userID.String(), nil)
	resp, err = app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)

	// Get stats
	req = httptest.NewRequest("GET", "/"+userID.String()+"/stats", nil)
	resp, err = app.Test(req)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}
