package service

import (
	"context"
	"errors"
	"log"

	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/repository"

	"golang.org/x/crypto/bcrypt"
)

var (
	ErrUserExists         = errors.New("user already exists")
	ErrInvalidCredentials = errors.New("invalid email or password")
	ErrUserNotFound       = errors.New("user not found")
)

// AuthServicer defines the interface for auth operations (used for mocking in tests)
type AuthServicer interface {
	Register(ctx context.Context, email, username, password string) (*model.User, error)
	Login(ctx context.Context, email, password string) (*TokenPair, *model.User, error)
	ValidateToken(token string) (*Claims, error)
	RefreshToken(ctx context.Context, token string) (*TokenPair, error)
	GetUserByID(ctx context.Context, id string) (*model.User, error)
	BanUser(ctx context.Context, userID string) error
	UnbanUser(ctx context.Context, userID string) error
	AssignRole(ctx context.Context, userID, roleCode string) error
}

type AuthService struct {
	userRepo   repository.UserRepository
	roleRepo   repository.RoleRepository
	jwtService *JWTService
	userSync   UserSyncClient // 跨库同步到 user-service；nil 表示跳过
}

func NewAuthService(userRepo repository.UserRepository, roleRepo repository.RoleRepository, jwtService *JWTService, syncClients ...UserSyncClient) *AuthService {
	s := &AuthService{userRepo: userRepo, roleRepo: roleRepo, jwtService: jwtService}
	if len(syncClients) > 0 && syncClients[0] != nil {
		s.userSync = syncClients[0]
	}
	return s
}

// resolveRolesAndPermissions 从 RBAC 表解析用户真实角色（含继承）与权限（含聚合去重）。
func (s *AuthService) resolveRolesAndPermissions(ctx context.Context, userID string) ([]string, []string, error) {
	roles, err := s.roleRepo.GetRolesForUser(ctx, userID)
	if err != nil {
		return nil, nil, err
	}
	perms, err := s.roleRepo.GetPermissionsForUser(ctx, userID)
	if err != nil {
		return nil, nil, err
	}
	roleCodes := make([]string, 0, len(roles))
	for _, r := range roles {
		roleCodes = append(roleCodes, r.Code)
	}
	if roleCodes == nil {
		roleCodes = []string{}
	}
	if perms == nil {
		perms = []string{}
	}
	return roleCodes, perms, nil
}

func (s *AuthService) Register(ctx context.Context, email, username, password string) (*model.User, error) {
	existing, _ := s.userRepo.FindByEmail(ctx, email)
	if existing != nil {
		return nil, ErrUserExists
	}
	existing, _ = s.userRepo.FindByUsername(ctx, username)
	if existing != nil {
		return nil, ErrUserExists
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), 12)
	if err != nil {
		return nil, err
	}
	user := &model.User{
		Email:        email,
		Username:     username,
		PasswordHash: string(hash),
	}
	if err := s.userRepo.Create(ctx, user); err != nil {
		return nil, err
	}
	// 默认分配 "user" 角色（失败不阻断注册）。
	if s.roleRepo != nil {
		if err := s.roleRepo.AssignRole(ctx, user.ID, "user"); err != nil {
			log.Printf("assign default role failed for user %s: %v", user.ID, err)
		}
	}
	// 跨库同步：注册成功后把用户同步到 user-service（幂等）。
	// 失败只记录日志，不阻断注册（保证主库写入优先）。
	if s.userSync != nil {
		if err := s.userSync.CreateUser(ctx, user.ID, user.Username, user.Email); err != nil {
			log.Printf("user-service sync failed for user %s: %v", user.ID, err)
		}
	}
	return user, nil
}

func (s *AuthService) Login(ctx context.Context, email, password string) (*TokenPair, *model.User, error) {
	user, err := s.userRepo.FindByEmail(ctx, email)
	if err != nil || user == nil {
		return nil, nil, ErrInvalidCredentials
	}
	if user.Status != "ACTIVE" {
		return nil, nil, ErrInvalidCredentials
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(password)); err != nil {
		return nil, nil, ErrInvalidCredentials
	}
	roles, perms, err := s.resolveRolesAndPermissions(ctx, user.ID)
	if err != nil {
		log.Printf("resolve roles for %s failed: %v", user.ID, err)
		roles, perms = []string{"user"}, []string{}
	}
	tokens, err := s.jwtService.GenerateTokenPair(user.ID, roles, perms)
	if err != nil {
		return nil, nil, err
	}
	return tokens, user, nil
}

func (s *AuthService) ValidateToken(token string) (*Claims, error) {
	claims, err := s.jwtService.ParseToken(token)
	if err != nil {
		return nil, err
	}
	// 接口鉴权只接受 access token；refresh token 不能当 access 用（防止长效 token 直接调 API）
	if claims.TokenType != "access" {
		return nil, errors.New("access token required")
	}
	return claims, nil
}

func (s *AuthService) RefreshToken(ctx context.Context, token string) (*TokenPair, error) {
	claims, err := s.jwtService.ParseToken(token)
	if err != nil {
		return nil, err
	}
	// 仅 refresh token 可用于刷新，access token 直接拒绝
	if claims.TokenType != "refresh" {
		return nil, errors.New("invalid refresh token")
	}
	// 刷新时从 DB 重新解析角色/权限（不信任旧 claims，权限变更在刷新即生效）。
	roles, perms, err := s.resolveRolesAndPermissions(ctx, claims.UserID)
	if err != nil {
		log.Printf("resolve roles for %s failed: %v", claims.UserID, err)
		roles, perms = claims.Roles, claims.Permissions
	}
	return s.jwtService.GenerateTokenPair(claims.UserID, roles, perms)
}

func (s *AuthService) GetUserByID(ctx context.Context, id string) (*model.User, error) {
	user, err := s.userRepo.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, ErrUserNotFound
	}
	return user, nil
}

// BanUser 封禁用户（admin 端点用，需 user:ban 权限，由 handler/网关校验）。
func (s *AuthService) BanUser(ctx context.Context, userID string) error {
	return s.userRepo.UpdateStatus(ctx, userID, "BANNED")
}

// UnbanUser 解封用户（admin 端点用，需 user:ban 权限）。
func (s *AuthService) UnbanUser(ctx context.Context, userID string) error {
	return s.userRepo.UpdateStatus(ctx, userID, "ACTIVE")
}

// AssignRole 分配角色（admin 端点用，需 user:assign_role 权限）。校验角色存在后幂等分配。
func (s *AuthService) AssignRole(ctx context.Context, userID, roleCode string) error {
	role, err := s.roleRepo.FindRoleByCode(ctx, roleCode)
	if err != nil {
		return err
	}
	if role == nil {
		return errors.New("role not found: " + roleCode)
	}
	return s.roleRepo.AssignRole(ctx, userID, roleCode)
}
