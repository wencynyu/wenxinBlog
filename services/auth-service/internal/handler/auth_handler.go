package handler

import (
	"errors"
	"strconv"
	"strings"

	"wenxinblog/auth-service/internal/dto"
	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/repository"
	"wenxinblog/auth-service/internal/service"

	"github.com/gofiber/fiber/v2"
)

type AuthHandler struct {
	authService service.AuthServicer
}

func NewAuthHandler(authService service.AuthServicer) *AuthHandler {
	return &AuthHandler{authService: authService}
}

func (h *AuthHandler) Register(c *fiber.Ctx) error {
	var req dto.RegisterRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if err := validateRegisterRequest(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	user, err := h.authService.Register(c.Context(), req.Email, req.Username, req.Password)
	if err != nil {
		if err == service.ErrUserExists {
			return c.Status(409).JSON(dto.ErrorResponse{Code: 409, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.Status(201).JSON(dto.APIResponse{Code: 201, Message: "registered", Data: dto.UserResponse{
		ID: user.ID, Username: user.Username, Email: user.Email, AvatarURL: user.AvatarURL,
		Status: user.Status, TwoFAEnabled: user.TwoFAEnabled,
	}})
}

func (h *AuthHandler) Login(c *fiber.Ctx) error {
	var req dto.LoginRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if err := validateLoginRequest(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	tokens, user, err := h.authService.Login(c.Context(), req.Email, req.Password)
	if err != nil {
		if err == service.ErrInvalidCredentials {
			return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "login success", Data: dto.AuthResponse{
		User: &dto.UserResponse{ID: user.ID, Username: user.Username, Email: user.Email, AvatarURL: user.AvatarURL,
			Status: user.Status, TwoFAEnabled: user.TwoFAEnabled},
		Tokens: &dto.TokenResponse{AccessToken: tokens.AccessToken, RefreshToken: tokens.RefreshToken, ExpiresIn: tokens.ExpiresIn},
	}})
}

func (h *AuthHandler) RefreshToken(c *fiber.Ctx) error {
	var req dto.RefreshRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	tokens, err := h.authService.RefreshToken(c.Context(), req.RefreshToken)
	if err != nil {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "invalid refresh token"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "token refreshed", Data: dto.TokenResponse{
		AccessToken: tokens.AccessToken, RefreshToken: tokens.RefreshToken, ExpiresIn: tokens.ExpiresIn,
	}})
}

func (h *AuthHandler) GetCurrentUser(c *fiber.Ctx) error {
	userID := c.Locals("userId").(string)
	user, err := h.authService.GetUserByID(c.Context(), userID)
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: "internal error"})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: dto.UserResponse{
		ID: user.ID, Username: user.Username, Email: user.Email, AvatarURL: user.AvatarURL,
		Status: user.Status, TwoFAEnabled: user.TwoFAEnabled,
	}})
}

func (h *AuthHandler) Logout(c *fiber.Ctx) error {
	return c.JSON(dto.APIResponse{Code: 200, Message: "logged out"})
}

// ValidateToken 验证 Bearer token，返回 userId + roles。
// 供网关 AuthenticationFilter 调用（GET /api/v1/auth/validate）。
func (h *AuthHandler) ValidateToken(c *fiber.Ctx) error {
	authHeader := c.Get("Authorization")
	if len(authHeader) <= 7 || authHeader[:7] != "Bearer " {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "missing bearer token"})
	}
	token := authHeader[7:]
	claims, err := h.authService.ValidateToken(token)
	if err != nil || claims == nil {
		return c.Status(401).JSON(dto.ErrorResponse{Code: 401, Message: "invalid token"})
	}
	return c.JSON(fiber.Map{
		"userId":      claims.UserID,
		"email":       "",
		"roles":       claims.Roles,
		"permissions": claims.Permissions,
	})
}

// validateRegisterRequest 校验注册请求：email 非空且含 @、username 非空、password 长度 >= 8。
func validateRegisterRequest(req *dto.RegisterRequest) error {
	if err := validateEmail(req.Email); err != nil {
		return err
	}
	if strings.TrimSpace(req.Username) == "" {
		return errors.New("username is required")
	}
	if len(req.Password) < 8 {
		return errors.New("password must be at least 8 characters")
	}
	return nil
}

// validateLoginRequest 校验登录请求：email 非空且含 @、password 非空。
func validateLoginRequest(req *dto.LoginRequest) error {
	if err := validateEmail(req.Email); err != nil {
		return err
	}
	if req.Password == "" {
		return errors.New("password is required")
	}
	return nil
}

func validateEmail(email string) error {
	if strings.TrimSpace(email) == "" || !strings.Contains(email, "@") {
		return errors.New("invalid email")
	}
	return nil
}

func RegisterRoutes(api fiber.Router, authService service.AuthServicer) {
	h := NewAuthHandler(authService)
	auth := api.Group("/auth")
	auth.Post("/register", h.Register)
	auth.Post("/login", h.Login)
	auth.Post("/refresh", h.RefreshToken)
	auth.Post("/logout", h.Logout)
	auth.Get("/validate", h.ValidateToken)

	// 管理端点：网关 AuthorizationFilter 按路径映射 user:ban / user:assign_role，
	// handler 再校验一次（纵深防御，防绕过网关直连）。
	admin := api.Group("/admin/users")
	admin.Post("/:id/ban", h.BanUser)
	admin.Post("/:id/unban", h.UnbanUser)
	admin.Post("/:id/roles", h.AssignRole)

	// 角色/权限管理端点：网关 AuthorizationFilter 映射 role:manage，handler 再校验一次。
	rbac := api.Group("/admin")
	rbac.Get("/permissions", h.ListPermissions)
	rbac.Post("/permissions", h.CreatePermission)
	rbac.Delete("/permissions/:code", h.DeletePermission)
	rbac.Get("/roles", h.ListRoles)
	rbac.Get("/roles/:id", h.GetRoleDetail)
	rbac.Post("/roles", h.CreateRole)
	rbac.Delete("/roles/:id", h.DeleteRole)
	rbac.Post("/roles/:id/permissions", h.GrantRolePermissions)
	rbac.Delete("/roles/:id/permissions/:code", h.RevokeRolePermission)
	// 用户管理
	rbac.Get("/users", h.ListUsers)
	rbac.Get("/users/:id", h.GetUserDetail)
}

func (h *AuthHandler) BanUser(c *fiber.Ctx) error {
	if !hasPermission(c.Get("X-User-Permissions"), "user:ban") {
		return c.Status(403).JSON(dto.ErrorResponse{Code: 403, Message: "forbidden: need user:ban"})
	}
	if err := h.authService.BanUser(c.Context(), c.Params("id")); err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "user banned"})
}

func (h *AuthHandler) UnbanUser(c *fiber.Ctx) error {
	if !hasPermission(c.Get("X-User-Permissions"), "user:ban") {
		return c.Status(403).JSON(dto.ErrorResponse{Code: 403, Message: "forbidden: need user:ban"})
	}
	if err := h.authService.UnbanUser(c.Context(), c.Params("id")); err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "user unbanned"})
}

func (h *AuthHandler) AssignRole(c *fiber.Ctx) error {
	if !hasPermission(c.Get("X-User-Permissions"), "user:assign_role") {
		return c.Status(403).JSON(dto.ErrorResponse{Code: 403, Message: "forbidden: need user:assign_role"})
	}
	var req struct {
		Role string `json:"role"`
	}
	if err := c.BodyParser(&req); err != nil || req.Role == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "role is required"})
	}
	if err := h.authService.AssignRole(c.Context(), c.Params("id"), req.Role); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "role assigned"})
}

// hasPermission 判断逗号分隔的 X-User-Permissions 是否含指定权限。
func hasPermission(permissions, required string) bool {
	if permissions == "" {
		return false
	}
	for _, p := range strings.Split(permissions, ",") {
		if required == strings.TrimSpace(p) {
			return true
		}
	}
	return false
}

// requireRoleManage 校验调用方持有 role:manage；未持有则写 403 并返回 false。
func requireRoleManage(c *fiber.Ctx) bool {
	if !hasPermission(c.Get("X-User-Permissions"), "role:manage") {
		c.Status(403).JSON(dto.ErrorResponse{Code: 403, Message: "forbidden: need role:manage"})
		return false
	}
	return true
}

// parseRoleID 解析 path 中的角色 id；非法时已写 400 并返回 0。
func parseRoleID(c *fiber.Ctx) int64 {
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid role id"})
		return 0
	}
	return id
}

// --- 权限注册表管理 ---

func (h *AuthHandler) ListPermissions(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	perms, err := h.authService.ListPermissions(c.Context())
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: perms})
}

func (h *AuthHandler) CreatePermission(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	var req struct {
		Code        string  `json:"code"`
		Name        string  `json:"name"`
		Resource    string  `json:"resource"`
		Action      string  `json:"action"`
		Scope       *string `json:"scope"`
		Description string  `json:"description"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if req.Code == "" || req.Name == "" || req.Resource == "" || req.Action == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "code/name/resource/action are required"})
	}
	perm := &model.Permission{
		Code: req.Code, Name: req.Name, Resource: req.Resource,
		Action: req.Action, Scope: req.Scope, Description: req.Description,
	}
	if err := h.authService.CreatePermission(c.Context(), perm); err != nil {
		if errors.Is(err, repository.ErrPermissionExists) {
			return c.Status(409).JSON(dto.ErrorResponse{Code: 409, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.Status(201).JSON(dto.APIResponse{Code: 201, Message: "permission created", Data: perm})
}

func (h *AuthHandler) DeletePermission(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	code := c.Params("code")
	if code == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "permission code is required"})
	}
	if err := h.authService.DeletePermission(c.Context(), code); err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "permission deleted"})
}

// --- 角色管理 ---

func (h *AuthHandler) ListRoles(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	roles, err := h.authService.ListRoles(c.Context())
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: roles})
}

func (h *AuthHandler) GetRoleDetail(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	id := parseRoleID(c)
	if id == 0 {
		return nil
	}
	role, err := h.authService.GetRoleByID(c.Context(), id)
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	if role == nil {
		return c.Status(404).JSON(dto.ErrorResponse{Code: 404, Message: "role not found"})
	}
	perms, err := h.authService.GetRolePermissions(c.Context(), id)
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: fiber.Map{"role": role, "permissions": perms}})
}

func (h *AuthHandler) CreateRole(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	var req struct {
		Code        string `json:"code"`
		Name        string `json:"name"`
		Description string `json:"description"`
		ParentCode  string `json:"parentCode"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if req.Code == "" || req.Name == "" {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "code and name are required"})
	}
	id, err := h.authService.CreateRole(c.Context(), req.Code, req.Name, req.Description, req.ParentCode)
	if err != nil {
		if errors.Is(err, repository.ErrRoleExists) {
			return c.Status(409).JSON(dto.ErrorResponse{Code: 409, Message: err.Error()})
		}
		if errors.Is(err, repository.ErrRoleNotFound) || errors.Is(err, repository.ErrRoleSelfParent) {
			return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.Status(201).JSON(dto.APIResponse{Code: 201, Message: "role created", Data: fiber.Map{"id": id}})
}

func (h *AuthHandler) DeleteRole(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	id := parseRoleID(c)
	if id == 0 {
		return nil
	}
	if err := h.authService.DeleteRole(c.Context(), id); err != nil {
		if errors.Is(err, repository.ErrRoleIsSystem) {
			return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: err.Error()})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "role deleted"})
}

// --- 角色↔权限动态配置 ---

func (h *AuthHandler) GrantRolePermissions(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	id := parseRoleID(c)
	if id == 0 {
		return nil
	}
	var req struct {
		PermissionCodes []string `json:"permissionCodes"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "invalid request body"})
	}
	if len(req.PermissionCodes) == 0 {
		return c.Status(400).JSON(dto.ErrorResponse{Code: 400, Message: "permissionCodes are required"})
	}
	for _, code := range req.PermissionCodes {
		if err := h.authService.GrantRolePermission(c.Context(), id, code); err != nil {
			if errors.Is(err, repository.ErrPermissionNotFound) {
				return c.Status(404).JSON(dto.ErrorResponse{Code: 404, Message: "permission not found: " + code})
			}
			return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
		}
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "permissions granted"})
}

func (h *AuthHandler) RevokeRolePermission(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	id := parseRoleID(c)
	if id == 0 {
		return nil
	}
	code := c.Params("code")
	if err := h.authService.RevokeRolePermission(c.Context(), id, code); err != nil {
		if errors.Is(err, repository.ErrPermissionNotFound) {
			return c.Status(404).JSON(dto.ErrorResponse{Code: 404, Message: "permission not found: " + code})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "permission revoked"})
}

// --- 用户管理 ---

func (h *AuthHandler) ListUsers(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	page, _ := strconv.Atoi(c.Query("page", "1"))
	pageSize, _ := strconv.Atoi(c.Query("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 20
	}
	search := c.Query("search")
	items, total, err := h.authService.ListUsers(c.Context(), page, pageSize, search)
	if err != nil {
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	totalPages := 0
	if pageSize > 0 && total > 0 {
		totalPages = int((total + int64(pageSize) - 1) / int64(pageSize))
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: dto.PaginatedData{
		Items: items, Total: total, Page: page, PageSize: pageSize, TotalPages: totalPages,
	}})
}

func (h *AuthHandler) GetUserDetail(c *fiber.Ctx) error {
	if !requireRoleManage(c) {
		return nil
	}
	id := c.Params("id")
	user, perms, err := h.authService.GetUserDetail(c.Context(), id)
	if err != nil {
		if errors.Is(err, service.ErrUserNotFound) {
			return c.Status(404).JSON(dto.ErrorResponse{Code: 404, Message: "user not found"})
		}
		return c.Status(500).JSON(dto.ErrorResponse{Code: 500, Message: err.Error()})
	}
	return c.JSON(dto.APIResponse{Code: 200, Message: "ok", Data: fiber.Map{"user": user, "permissions": perms}})
}
