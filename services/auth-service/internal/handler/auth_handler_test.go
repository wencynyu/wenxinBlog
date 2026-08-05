package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http/httptest"
	"testing"

	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/repository"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type mockAuthService struct {
	registerFunc             func(ctx context.Context, email, username, password string) (*model.User, error)
	loginFunc                func(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error)
	validateTokenFunc        func(token string) (*service.Claims, error)
	refreshTokenFunc         func(ctx context.Context, token string) (*service.TokenPair, error)
	getUserByIDFunc          func(ctx context.Context, id string) (*model.User, error)
	banUserFunc              func(ctx context.Context, userID string) error
	unbanUserFunc            func(ctx context.Context, userID string) error
	assignRoleFunc           func(ctx context.Context, userID, roleCode string) error
	listPermissionsFunc      func(ctx context.Context) ([]model.Permission, error)
	createPermissionFunc     func(ctx context.Context, perm *model.Permission) error
	deletePermissionFunc     func(ctx context.Context, code string) error
	listRolesFunc            func(ctx context.Context) ([]model.Role, error)
	getRoleByIDFunc          func(ctx context.Context, id int64) (*model.Role, error)
	getRolePermissionsFunc   func(ctx context.Context, roleID int64) ([]model.Permission, error)
	createRoleFunc           func(ctx context.Context, code, name, description, parentCode string) (int64, error)
	deleteRoleFunc           func(ctx context.Context, id int64) error
	grantRolePermissionFunc  func(ctx context.Context, roleID int64, permCode string) error
	revokeRolePermissionFunc func(ctx context.Context, roleID int64, permCode string) error
	listUsersFunc            func(ctx context.Context, page, pageSize int, search string) ([]model.AdminUser, int64, error)
	getUserDetailFunc        func(ctx context.Context, userID string) (*model.AdminUser, []string, error)
}

func (m *mockAuthService) Register(ctx context.Context, email, username, password string) (*model.User, error) {
	if m.registerFunc != nil {
		return m.registerFunc(ctx, email, username, password)
	}
	return nil, nil
}

func (m *mockAuthService) Login(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error) {
	if m.loginFunc != nil {
		return m.loginFunc(ctx, email, password)
	}
	return nil, nil, nil
}

func (m *mockAuthService) ValidateToken(token string) (*service.Claims, error) {
	if m.validateTokenFunc != nil {
		return m.validateTokenFunc(token)
	}
	return nil, nil
}

func (m *mockAuthService) RefreshToken(ctx context.Context, token string) (*service.TokenPair, error) {
	if m.refreshTokenFunc != nil {
		return m.refreshTokenFunc(ctx, token)
	}
	return nil, nil
}

func (m *mockAuthService) GetUserByID(ctx context.Context, id string) (*model.User, error) {
	if m.getUserByIDFunc != nil {
		return m.getUserByIDFunc(ctx, id)
	}
	return nil, nil
}

func (m *mockAuthService) BanUser(ctx context.Context, userID string) error {
	if m.banUserFunc != nil {
		return m.banUserFunc(ctx, userID)
	}
	return nil
}

func (m *mockAuthService) UnbanUser(ctx context.Context, userID string) error {
	if m.unbanUserFunc != nil {
		return m.unbanUserFunc(ctx, userID)
	}
	return nil
}

func (m *mockAuthService) AssignRole(ctx context.Context, userID, roleCode string) error {
	if m.assignRoleFunc != nil {
		return m.assignRoleFunc(ctx, userID, roleCode)
	}
	return nil
}

func (m *mockAuthService) ListPermissions(ctx context.Context) ([]model.Permission, error) {
	if m.listPermissionsFunc != nil {
		return m.listPermissionsFunc(ctx)
	}
	return nil, nil
}
func (m *mockAuthService) CreatePermission(ctx context.Context, perm *model.Permission) error {
	if m.createPermissionFunc != nil {
		return m.createPermissionFunc(ctx, perm)
	}
	return nil
}
func (m *mockAuthService) DeletePermission(ctx context.Context, code string) error {
	if m.deletePermissionFunc != nil {
		return m.deletePermissionFunc(ctx, code)
	}
	return nil
}
func (m *mockAuthService) ListRoles(ctx context.Context) ([]model.Role, error) {
	if m.listRolesFunc != nil {
		return m.listRolesFunc(ctx)
	}
	return nil, nil
}
func (m *mockAuthService) GetRoleByID(ctx context.Context, id int64) (*model.Role, error) {
	if m.getRoleByIDFunc != nil {
		return m.getRoleByIDFunc(ctx, id)
	}
	return nil, nil
}
func (m *mockAuthService) GetRolePermissions(ctx context.Context, roleID int64) ([]model.Permission, error) {
	if m.getRolePermissionsFunc != nil {
		return m.getRolePermissionsFunc(ctx, roleID)
	}
	return nil, nil
}
func (m *mockAuthService) CreateRole(ctx context.Context, code, name, description, parentCode string) (int64, error) {
	if m.createRoleFunc != nil {
		return m.createRoleFunc(ctx, code, name, description, parentCode)
	}
	return 0, nil
}
func (m *mockAuthService) DeleteRole(ctx context.Context, id int64) error {
	if m.deleteRoleFunc != nil {
		return m.deleteRoleFunc(ctx, id)
	}
	return nil
}
func (m *mockAuthService) GrantRolePermission(ctx context.Context, roleID int64, permCode string) error {
	if m.grantRolePermissionFunc != nil {
		return m.grantRolePermissionFunc(ctx, roleID, permCode)
	}
	return nil
}
func (m *mockAuthService) RevokeRolePermission(ctx context.Context, roleID int64, permCode string) error {
	if m.revokeRolePermissionFunc != nil {
		return m.revokeRolePermissionFunc(ctx, roleID, permCode)
	}
	return nil
}
func (m *mockAuthService) ListUsers(ctx context.Context, page, pageSize int, search string) ([]model.AdminUser, int64, error) {
	if m.listUsersFunc != nil {
		return m.listUsersFunc(ctx, page, pageSize, search)
	}
	return nil, 0, nil
}
func (m *mockAuthService) GetUserDetail(ctx context.Context, userID string) (*model.AdminUser, []string, error) {
	if m.getUserDetailFunc != nil {
		return m.getUserDetailFunc(ctx, userID)
	}
	return nil, nil, nil
}

func setupApp(svc service.AuthServicer) *fiber.App {
	app := fiber.New(fiber.Config{DisableStartupMessage: true, AppName: "Test"})
	h := NewAuthHandler(svc)
	app.Post("/register", h.Register)
	app.Post("/login", h.Login)
	app.Post("/refresh", h.RefreshToken)
	app.Post("/logout", h.Logout)
	app.Get("/me", func(c *fiber.Ctx) error {
		c.Locals("userId", "test-user-id")
		return h.GetCurrentUser(c)
	})
	// admin 角色/权限管理端点（测试用 X-User-Permissions 头模拟网关注入）
	admin := app.Group("/api/v1/admin")
	admin.Get("/permissions", h.ListPermissions)
	admin.Post("/permissions", h.CreatePermission)
	admin.Delete("/permissions/:code", h.DeletePermission)
	admin.Get("/roles", h.ListRoles)
	admin.Get("/roles/:id", h.GetRoleDetail)
	admin.Post("/roles", h.CreateRole)
	admin.Delete("/roles/:id", h.DeleteRole)
	admin.Post("/roles/:id/permissions", h.GrantRolePermissions)
	admin.Delete("/roles/:id/permissions/:code", h.RevokeRolePermission)
	admin.Get("/users", h.ListUsers)
	admin.Get("/users/:id", h.GetUserDetail)
	return app
}

func TestRegister_InvalidBody(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader([]byte("invalid json")))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRegister_Success(t *testing.T) {
	svc := &mockAuthService{
		registerFunc: func(ctx context.Context, email, username, password string) (*model.User, error) {
			return &model.User{ID: "user-1", Email: email, Username: username, Status: "ACTIVE"}, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 201, resp.StatusCode)
}

func TestRegister_UserExists(t *testing.T) {
	svc := &mockAuthService{
		registerFunc: func(ctx context.Context, email, username, password string) (*model.User, error) {
			return nil, service.ErrUserExists
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 409, resp.StatusCode)
}

func TestRegister_InternalError(t *testing.T) {
	svc := &mockAuthService{
		registerFunc: func(ctx context.Context, email, username, password string) (*model.User, error) {
			return nil, errors.New("db error")
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 500, resp.StatusCode)
}

func TestRegister_WeakPassword(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "test", "password": "123"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRegister_InvalidEmail(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "not-an-email", "username": "test", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRegister_EmptyUsername(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "username": "", "password": "12345678"})
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestLogin_InvalidBody(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("POST", "/login", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestLogin_Success(t *testing.T) {
	jwtSvc := service.NewJWTService("test-secret")
	tokens, _ := jwtSvc.GenerateTokenPair("user-123", []string{"USER"}, nil)
	svc := &mockAuthService{
		loginFunc: func(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error) {
			return tokens, &model.User{ID: "user-123", Email: email, Username: "testuser", Status: "ACTIVE"}, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "password": "123456"})
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestLogin_InvalidCredentials(t *testing.T) {
	svc := &mockAuthService{
		loginFunc: func(ctx context.Context, email, password string) (*service.TokenPair, *model.User, error) {
			return nil, nil, service.ErrInvalidCredentials
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "a@b.com", "password": "wrong"})
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestLogin_InvalidEmail(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"email": "not-an-email", "password": "12345678"})
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestRefreshToken_Success(t *testing.T) {
	jwtSvc := service.NewJWTService("test-secret")
	tokens, _ := jwtSvc.GenerateTokenPair("user-123", []string{"USER"}, nil)
	svc := &mockAuthService{
		refreshTokenFunc: func(ctx context.Context, token string) (*service.TokenPair, error) {
			return tokens, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"refreshToken": tokens.RefreshToken})
	req := httptest.NewRequest("POST", "/refresh", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestRefreshToken_Invalid(t *testing.T) {
	svc := &mockAuthService{
		refreshTokenFunc: func(ctx context.Context, token string) (*service.TokenPair, error) {
			return nil, errors.New("invalid token")
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"refreshToken": "bad-token"})
	req := httptest.NewRequest("POST", "/refresh", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 401, resp.StatusCode)
}

func TestGetMe_Success(t *testing.T) {
	svc := &mockAuthService{
		getUserByIDFunc: func(ctx context.Context, id string) (*model.User, error) {
			return &model.User{ID: id, Email: "a@b.com", Username: "testuser", Status: "ACTIVE"}, nil
		},
	}
	app := setupApp(svc)

	req := httptest.NewRequest("GET", "/me", nil)
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestLogout(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("POST", "/logout", nil)
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

// --- 角色/权限管理端点 ---

func TestListPermissions_Forbidden(t *testing.T) {
	svc := &mockAuthService{}
	app := setupApp(svc)

	req := httptest.NewRequest("GET", "/api/v1/admin/permissions", nil)
	resp, err := app.Test(req, -1) // 无 X-User-Permissions → 403
	require.NoError(t, err)
	assert.Equal(t, 403, resp.StatusCode)
}

func TestListPermissions_Success(t *testing.T) {
	svc := &mockAuthService{
		listPermissionsFunc: func(ctx context.Context) ([]model.Permission, error) {
			return []model.Permission{{Code: "post:read", Name: "查看博文"}}, nil
		},
	}
	app := setupApp(svc)

	req := httptest.NewRequest("GET", "/api/v1/admin/permissions", nil)
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestCreatePermission_Success(t *testing.T) {
	var got *model.Permission
	svc := &mockAuthService{
		createPermissionFunc: func(ctx context.Context, perm *model.Permission) error {
			got = perm
			return nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"code": "custom:x", "name": "X", "resource": "custom", "action": "x"})
	req := httptest.NewRequest("POST", "/api/v1/admin/permissions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 201, resp.StatusCode)
	require.NotNil(t, got)
	assert.Equal(t, "custom:x", got.Code)
}

func TestCreateRole_Success(t *testing.T) {
	svc := &mockAuthService{
		createRoleFunc: func(ctx context.Context, code, name, description, parentCode string) (int64, error) {
			assert.Equal(t, "editor", code)
			assert.Equal(t, "user", parentCode)
			return 6, nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string]string{"code": "editor", "name": "编辑", "parentCode": "user"})
	req := httptest.NewRequest("POST", "/api/v1/admin/roles", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 201, resp.StatusCode)
}

func TestDeleteRole_SystemProtected(t *testing.T) {
	svc := &mockAuthService{
		deleteRoleFunc: func(ctx context.Context, id int64) error {
			return repository.ErrRoleIsSystem
		},
	}
	app := setupApp(svc)

	req := httptest.NewRequest("DELETE", "/api/v1/admin/roles/5", nil)
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 400, resp.StatusCode)
}

func TestGrantRolePermissions_Success(t *testing.T) {
	granted := []string{}
	svc := &mockAuthService{
		grantRolePermissionFunc: func(ctx context.Context, roleID int64, permCode string) error {
			assert.Equal(t, int64(3), roleID)
			granted = append(granted, permCode)
			return nil
		},
	}
	app := setupApp(svc)

	body, _ := json.Marshal(map[string][]string{"permissionCodes": {"post:create", "post:read"}})
	req := httptest.NewRequest("POST", "/api/v1/admin/roles/3/permissions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
	assert.Equal(t, []string{"post:create", "post:read"}, granted)
}

func TestListUsers_Success(t *testing.T) {
	svc := &mockAuthService{
		listUsersFunc: func(ctx context.Context, page, pageSize int, search string) ([]model.AdminUser, int64, error) {
			return []model.AdminUser{{ID: "u1", Username: "alice", Email: "a@t.com", Status: "ACTIVE", Roles: []string{"user"}}}, 1, nil
		},
	}
	app := setupApp(svc)

	req := httptest.NewRequest("GET", "/api/v1/admin/users?page=1&pageSize=20", nil)
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 200, resp.StatusCode)
}

func TestGetUserDetail_NotFound(t *testing.T) {
	svc := &mockAuthService{
		getUserDetailFunc: func(ctx context.Context, userID string) (*model.AdminUser, []string, error) {
			return nil, nil, service.ErrUserNotFound
		},
	}
	app := setupApp(svc)

	req := httptest.NewRequest("GET", "/api/v1/admin/users/u1", nil)
	req.Header.Set("X-User-Permissions", "role:manage")
	resp, err := app.Test(req, -1)
	require.NoError(t, err)
	assert.Equal(t, 404, resp.StatusCode)
}
