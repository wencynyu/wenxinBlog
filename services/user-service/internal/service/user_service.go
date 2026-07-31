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
	GetProfile(userID uuid.UUID) (*dto.UserProfileResponse, error)
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

func (s *UserService) GetProfile(userID uuid.UUID) (*dto.UserProfileResponse, error) {
	profile, err := s.profileRepo.GetByUserID(userID)
	if err != nil {
		return nil, err
	}
	// 懒创建：profile 不存在时建一条默认的。否则注册流程未建 profile 的用户，
	// 其「我的主页」会恒返回 404（user_profiles 表此前从未被写入）。
	if profile == nil {
		if profile, err = s.ensureProfile(userID); err != nil {
			return nil, err
		}
	}
	resp := dto.ProfileFromModel(*profile)
	go s.profileRepo.IncrementViewCount(userID)
	return &resp, nil
}

// ensureProfile 为尚无 profile 的用户创建一条默认 profile（首次访问个人主页时触发）。
// display_name 用 users 表的 username 作默认值（user-service 同库可读）。
// 并发场景下可能已有另一请求创建成功，此时 Create 会因 user_id 唯一约束失败 → 回退再查一次。
func (s *UserService) ensureProfile(userID uuid.UUID) (*model.UserProfile, error) {
	username, err := s.profileRepo.GetUsername(userID)
	if err != nil {
		return nil, err
	}
	displayName := username
	if displayName == "" {
		displayName = "user" // users.username NOT NULL，正常非空；兜底防 display_name NOT NULL 违约
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

	if err := s.profileRepo.Update(userID, req.DisplayName, req.AvatarUrl, req.Bio, req.Website, req.Location, req.Company, birthday); err != nil {
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
	return &resp, nil
}

func (s *UserService) SearchUsers(query string, page, size int) (*dto.UserListResponse, error) {
	profiles, total, err := s.profileRepo.Search(query, size, (page-1)*size)
	if err != nil {
		return nil, err
	}

	var users []dto.UserProfileResponse
	for _, p := range profiles {
		users = append(users, dto.ProfileFromModel(p))
	}
	if users == nil {
		users = []dto.UserProfileResponse{}
	}
	return &dto.UserListResponse{Users: users, Total: total}, nil
}

func (s *UserService) FollowUser(followerID, followingID uuid.UUID) error {
	if followerID == followingID {
		return nil
	}

	if err := s.followRepo.Follow(followerID, followingID); err != nil {
		return err
	}

	// Update stats async
	go func() {
		s.statsRepo.IncrementFollowerCount(followingID)
		s.statsRepo.IncrementFollowingCount(followerID)
	}()
	return nil
}

func (s *UserService) UnfollowUser(followerID, followingID uuid.UUID) error {
	if err := s.followRepo.Unfollow(followerID, followingID); err != nil {
		return err
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

	var users []dto.UserProfileResponse
	for _, p := range profiles {
		users = append(users, dto.ProfileFromModel(p))
	}
	if users == nil {
		users = []dto.UserProfileResponse{}
	}
	return &dto.UserListResponse{Users: users, Total: total}, nil
}

func (s *UserService) GetFollowing(userID uuid.UUID, page, size int) (*dto.UserListResponse, error) {
	profiles, total, err := s.followRepo.GetFollowing(userID, size, (page-1)*size)
	if err != nil {
		return nil, err
	}

	var users []dto.UserProfileResponse
	for _, p := range profiles {
		users = append(users, dto.ProfileFromModel(p))
	}
	if users == nil {
		users = []dto.UserProfileResponse{}
	}
	return &dto.UserListResponse{Users: users, Total: total}, nil
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

// Suppress unused import
var _ = model.UserProfile{}
