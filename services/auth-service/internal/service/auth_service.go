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

	// 权限注册表管理（需 role:manage）。
	ListPermissions(ctx context.Context) ([]model.Permission, error)
	CreatePermission(ctx context.Context, perm *model.Permission) error
	DeletePermission(ctx context.Context, code string) error
	// 角色管理（需 role:manage）。
	ListRoles(ctx context.Context) ([]model.Role, error)
	GetRoleByID(ctx context.Context, id int64) (*model.Role, error)
	GetRolePermissions(ctx context.Context, roleID int64) ([]model.Permission, error)
	CreateRole(ctx context.Context, code, name, description, parentCode string) (int64, error)
	DeleteRole(ctx context.Context, id int64) error
	// 角色↔权限动态配置（需 role:manage）。
	GrantRolePermission(ctx context.Context, roleID int64, permCode string) error
	RevokeRolePermission(ctx context.Context, roleID int64, permCode string) error
	// 用户管理（需 role:manage）。
	ListUsers(ctx context.Context, page, pageSize int, search string) ([]model.AdminUser, int64, error)
	GetUserDetail(ctx context.Context, userID string) (*model.AdminUser, []string, error)
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
	perms, err := s.roleRepo.GetPermissionsForUser(ctx, userID) // []model.Permission
	if err != nil {
		return nil, nil, err
	}
	roleCodes := make([]string, 0, len(roles))
	for _, r := range roles {
		roleCodes = append(roleCodes, r.Code)
	}
	permCodes := make([]string, 0, len(perms))
	for _, p := range perms {
		permCodes = append(permCodes, p.Code)
	}
	return roleCodes, permCodes, nil
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
	tokens, err := s.issueTokens(ctx, user.ID)
	if err != nil {
		return nil, nil, err
	}
	return tokens, user, nil
}

// issueTokens 解析用户角色/权限（含继承）并签发 access+refresh token 对。
// 登录/社交登录/手机号登录三方复用：密码校验或第三方身份确认通过后调用。
// 角色解析失败时降级为 ["user"]（与原 Login 行为一致）。
func (s *AuthService) issueTokens(ctx context.Context, userID string) (*TokenPair, error) {
	roles, perms, err := s.resolveRolesAndPermissions(ctx, userID)
	if err != nil {
		log.Printf("resolve roles for %s failed: %v", userID, err)
		roles, perms = []string{"user"}, []string{}
	}
	return s.jwtService.GenerateTokenPair(userID, roles, perms)
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

// --- 角色/权限管理（admin 端点用，需 role:manage，由 handler/网关校验） ---

func (s *AuthService) ListPermissions(ctx context.Context) ([]model.Permission, error) {
	return s.roleRepo.GetAllPermissions(ctx)
}

func (s *AuthService) CreatePermission(ctx context.Context, perm *model.Permission) error {
	return s.roleRepo.CreatePermission(ctx, perm)
}

func (s *AuthService) DeletePermission(ctx context.Context, code string) error {
	return s.roleRepo.DeletePermission(ctx, code)
}

func (s *AuthService) ListRoles(ctx context.Context) ([]model.Role, error) {
	return s.roleRepo.GetAllRoles(ctx)
}

func (s *AuthService) GetRoleByID(ctx context.Context, id int64) (*model.Role, error) {
	return s.roleRepo.GetRoleByID(ctx, id)
}

func (s *AuthService) GetRolePermissions(ctx context.Context, roleID int64) ([]model.Permission, error) {
	return s.roleRepo.GetPermissionsForRole(ctx, roleID)
}

func (s *AuthService) CreateRole(ctx context.Context, code, name, description, parentCode string) (int64, error) {
	return s.roleRepo.CreateRole(ctx, code, name, description, parentCode)
}

func (s *AuthService) DeleteRole(ctx context.Context, id int64) error {
	return s.roleRepo.DeleteRole(ctx, id)
}

func (s *AuthService) GrantRolePermission(ctx context.Context, roleID int64, permCode string) error {
	return s.roleRepo.GrantPermissionToRole(ctx, roleID, permCode)
}

func (s *AuthService) RevokeRolePermission(ctx context.Context, roleID int64, permCode string) error {
	return s.roleRepo.RevokePermissionFromRole(ctx, roleID, permCode)
}

// --- 用户管理（admin 端点用，需 role:manage） ---

// ListUsers 分页返回用户列表，每项含角色 code（量小，逐用户解析可接受）。
func (s *AuthService) ListUsers(ctx context.Context, page, pageSize int, search string) ([]model.AdminUser, int64, error) {
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}
	users, total, err := s.userRepo.ListUsers(ctx, page, pageSize, search)
	if err != nil {
		return nil, 0, err
	}
	items := make([]model.AdminUser, 0, len(users))
	for _, u := range users {
		roles, err := s.roleRepo.GetRolesForUser(ctx, u.ID)
		if err != nil {
			return nil, 0, err
		}
		items = append(items, toAdminUser(u, roleCodes(roles)))
	}
	return items, total, nil
}

// GetUserDetail 返回单用户视图（含角色）+ 权限 code 列表。
func (s *AuthService) GetUserDetail(ctx context.Context, userID string) (*model.AdminUser, []string, error) {
	user, err := s.userRepo.FindByID(ctx, userID)
	if err != nil {
		return nil, nil, err
	}
	if user == nil {
		return nil, nil, ErrUserNotFound
	}
	roles, err := s.roleRepo.GetRolesForUser(ctx, userID)
	if err != nil {
		return nil, nil, err
	}
	perms, err := s.roleRepo.GetPermissionsForUser(ctx, userID)
	if err != nil {
		return nil, nil, err
	}
	permCodes := make([]string, 0, len(perms))
	for _, p := range perms {
		permCodes = append(permCodes, p.Code)
	}
	au := toAdminUser(*user, roleCodes(roles))
	return &au, permCodes, nil
}

func toAdminUser(u model.User, roles []string) model.AdminUser {
	return model.AdminUser{
		ID: u.ID, Username: u.Username, Email: u.Email,
		AvatarURL: u.AvatarURL, Status: u.Status, CreatedAt: u.CreatedAt,
		Roles: roles,
	}
}

func roleCodes(roles []model.Role) []string {
	codes := make([]string, 0, len(roles))
	for _, r := range roles {
		codes = append(codes, r.Code)
	}
	return codes
}
