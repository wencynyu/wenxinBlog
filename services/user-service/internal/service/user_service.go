package service

import (
	"database/sql"
	"time"

	"wenxinblog/user-service/internal/dto"
	"wenxinblog/user-service/internal/model"
	"wenxinblog/user-service/internal/repository"

	"github.com/google/uuid"
)

// UserServicer defines the interface for user service operations
type UserServicer interface {
	GetProfile(userID uuid.UUID, currentUserID *uuid.UUID) (*dto.UserProfileResponse, error)
	UpdateProfile(userID uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error)
	SearchUsers(query string, page, size int) (*dto.UserListResponse, error)
	FollowUser(followerID, followingID uuid.UUID) error
	UnfollowUser(followerID, followingID uuid.UUID) error
	GetFollowers(userID uuid.UUID, page, size int) (*dto.UserListResponse, error)
	GetFollowing(userID uuid.UUID, page, size int) (*dto.UserListResponse, error)
	IsFollowing(followerID, followingID uuid.UUID) (bool, error)
	GetFollowingIDs(userID uuid.UUID) ([]uuid.UUID, error)
	GetStats(userID uuid.UUID) (*dto.StatsResponse, error)
	CreateUser(userID uuid.UUID, username, email string) error
}

type UserService struct {
	profileRepo repository.ProfileRepositoryInterface
	followRepo  repository.FollowRepositoryInterface
	statsRepo   repository.StatsRepositoryInterface
}

func NewUserService(pr repository.ProfileRepositoryInterface, fr repository.FollowRepositoryInterface, sr repository.StatsRepositoryInterface) *UserService {
	return &UserService{profileRepo: pr, followRepo: fr, statsRepo: sr}
}

// CreateUser 幂等插入 users 表（auth-service 注册成功后调用，跨库同步）。
func (s *UserService) CreateUser(userID uuid.UUID, username, email string) error {
	return s.profileRepo.CreateUser(userID, username, email)
}

// GetProfile 返回完整用户主页：profile + username/email + 统计 + isFollowing（当前用户视角）。
func (s *UserService) GetProfile(userID uuid.UUID, currentUserID *uuid.UUID) (*dto.UserProfileResponse, error) {
	profile, err := s.profileRepo.GetByUserID(userID)
	if err != nil {
		return nil, err
	}
	// 懒创建：profile 不存在时建一条默认的。
	if profile == nil {
		if profile, err = s.ensureProfile(userID); err != nil {
			return nil, err
		}
	}
	resp := dto.ProfileFromModel(*profile)
	s.enrichProfile(&resp, userID, currentUserID)
	go s.profileRepo.IncrementViewCount(userID)
	return &resp, nil
}

// enrichProfile 补填 username/email/统计/isFollowing（ProfileFromModel 只填 profile 基础）。
// statsRepo/followRepo 容错 nil（测试或部分构造场景），enrichment 失败不阻断主流程。
func (s *UserService) enrichProfile(resp *dto.UserProfileResponse, userID uuid.UUID, currentUserID *uuid.UUID) {
	if s.profileRepo != nil {
		if username, email, err := s.profileRepo.GetUserinfo(userID); err == nil {
			resp.Username = username
			resp.Email = email
		}
	}
	if s.statsRepo != nil {
		if stats, err := s.statsRepo.GetStats(userID); err == nil && stats != nil {
			resp.FollowersCount = stats.FollowerCount
			resp.FollowingCount = stats.FollowingCount
			resp.PostsCount = stats.PostCount
		}
	}
	if s.followRepo != nil && currentUserID != nil && *currentUserID != userID {
		if following, err := s.followRepo.IsFollowing(*currentUserID, userID); err == nil {
			resp.IsFollowing = following
		}
	}
}

// enrichUsername 列表项补 username（逐个查，pageSize 小可接受）。容错 nil profileRepo。
func (s *UserService) enrichUsername(resp *dto.UserProfileResponse, userID uuid.UUID) {
	if s.profileRepo == nil {
		return
	}
	if username, _, err := s.profileRepo.GetUserinfo(userID); err == nil {
		resp.Username = username
	}
}

// ensureProfile 为尚无 profile 的用户创建默认 profile（display_name 用 users.username）。
func (s *UserService) ensureProfile(userID uuid.UUID) (*model.UserProfile, error) {
	username, err := s.profileRepo.GetUsername(userID)
	if err != nil {
		return nil, err
	}
	displayName := username
	if displayName == "" {
		displayName = "user"
	}
	now := time.Now()
	p := &model.UserProfile{
		UserID:      userID,
		DisplayName: sql.NullString{String: displayName, Valid: true},
		CreatedAt:   now,
		UpdatedAt:   now,
	}
	if err := s.profileRepo.Create(p); err != nil {
		if existing, getErr := s.profileRepo.GetByUserID(userID); getErr == nil && existing != nil {
			return existing, nil
		}
		return nil, err
	}
	return p, nil
}

func (s *UserService) UpdateProfile(userID uuid.UUID, req *dto.UpdateProfileRequest) (*dto.UserProfileResponse, error) {
	var birthday *time.Time
	if req.Birthday != nil {
		t, err := time.Parse("2006-01-02", *req.Birthday)
		if err != nil {
			return nil, err
		}
		birthday = &t
	}

	if err := s.profileRepo.Update(userID, req.DisplayName, req.Avatar, req.Bio, req.Website, req.Location, req.Company, birthday); err != nil {
		return nil, err
	}

	profile, err := s.profileRepo.GetByUserID(userID)
	if err != nil {
		return nil, err
	}
	if profile == nil {
		return nil, nil
	}
	resp := dto.ProfileFromModel(*profile)
	s.enrichProfile(&resp, userID, nil)
	return &resp, nil
}

func (s *UserService) SearchUsers(query string, page, size int) (*dto.UserListResponse, error) {
	profiles, total, err := s.profileRepo.Search(query, size, (page-1)*size)
	if err != nil {
		return nil, err
	}
	items := make([]dto.UserProfileResponse, 0, len(profiles))
	for _, p := range profiles {
		resp := dto.ProfileFromModel(p)
		s.enrichUsername(&resp, p.UserID)
		items = append(items, resp)
	}
	return paged(items, total, page, size), nil
}

func (s *UserService) FollowUser(followerID, followingID uuid.UUID) error {
	if followerID == followingID {
		return nil
	}
	inserted, err := s.followRepo.Follow(followerID, followingID)
	if err != nil {
		return err
	}
	if !inserted {
		return nil
	}
	go func() {
		s.statsRepo.IncrementFollowerCount(followingID)
		s.statsRepo.IncrementFollowingCount(followerID)
	}()
	return nil
}

func (s *UserService) UnfollowUser(followerID, followingID uuid.UUID) error {
	deleted, err := s.followRepo.Unfollow(followerID, followingID)
	if err != nil {
		return err
	}
	if !deleted {
		return nil
	}
	go func() {
		s.statsRepo.DecrementFollowerCount(followingID)
		s.statsRepo.DecrementFollowingCount(followerID)
	}()
	return nil
}

func (s *UserService) GetFollowers(userID uuid.UUID, page, size int) (*dto.UserListResponse, error) {
	profiles, total, err := s.followRepo.GetFollowers(userID, size, (page-1)*size)
	if err != nil {
		return nil, err
	}
	items := make([]dto.UserProfileResponse, 0, len(profiles))
	for _, p := range profiles {
		resp := dto.ProfileFromModel(p)
		s.enrichUsername(&resp, p.UserID)
		items = append(items, resp)
	}
	return paged(items, total, page, size), nil
}

func (s *UserService) GetFollowing(userID uuid.UUID, page, size int) (*dto.UserListResponse, error) {
	profiles, total, err := s.followRepo.GetFollowing(userID, size, (page-1)*size)
	if err != nil {
		return nil, err
	}
	items := make([]dto.UserProfileResponse, 0, len(profiles))
	for _, p := range profiles {
		resp := dto.ProfileFromModel(p)
		s.enrichUsername(&resp, p.UserID)
		items = append(items, resp)
	}
	return paged(items, total, page, size), nil
}

func (s *UserService) IsFollowing(followerID, followingID uuid.UUID) (bool, error) {
	return s.followRepo.IsFollowing(followerID, followingID)
}

func (s *UserService) GetFollowingIDs(userID uuid.UUID) ([]uuid.UUID, error) {
	return s.followRepo.GetFollowingIDs(userID)
}

func (s *UserService) GetStats(userID uuid.UUID) (*dto.StatsResponse, error) {
	stats, err := s.statsRepo.GetStats(userID)
	if err != nil {
		return nil, err
	}
	return &dto.StatsResponse{
		PostCount:      stats.PostCount,
		FollowerCount:  stats.FollowerCount,
		FollowingCount: stats.FollowingCount,
		LikeCount:      stats.LikeCount,
	}, nil
}

// paged 构造分页响应（对齐前端 PaginatedResponse）。
func paged(items []dto.UserProfileResponse, total int64, page, size int) *dto.UserListResponse {
	totalPages := 0
	if size > 0 && total > 0 {
		totalPages = int((total + int64(size) - 1) / int64(size))
	}
	return &dto.UserListResponse{
		Items: items, Total: total, Page: page, PageSize: size, TotalPages: totalPages,
	}
}
