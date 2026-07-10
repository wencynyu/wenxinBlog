package handler

import (
	"strconv"

	"wenxinblog/user-service/internal/config"
	"wenxinblog/user-service/internal/dto"
	"wenxinblog/user-service/internal/middleware"
	"wenxinblog/user-service/internal/service"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
)

type UserHandler struct {
	svc service.UserServicer
}

func NewUserHandler(svc service.UserServicer) *UserHandler {
	return &UserHandler{svc: svc}
}

func RegisterRoutes(api fiber.Router, cfg *config.Config) {
	svc := service.NewUserService(nil, nil, nil) // placeholder, real wiring in main.go
	h := NewUserHandler(svc)

	users := api.Group("/users")

	// Public routes
	users.Get("/:id", h.GetProfile)
	users.Get("/:id/stats", h.GetStats)
	users.Get("/:id/followers", h.GetFollowers)
	users.Get("/:id/following", h.GetFollowing)
	users.Get("/search", h.SearchUsers)

	// Auth-required routes
	users.Put("/:id", middleware.AuthMiddleware(), h.UpdateProfile)
	users.Post("/:id/follow", middleware.AuthMiddleware(), h.FollowUser)
	users.Delete("/:id/follow", middleware.AuthMiddleware(), h.UnfollowUser)

	api.Get("/me/following", middleware.AuthMiddleware(), h.GetMyFollowing)
}

func (h *UserHandler) GetProfile(c *fiber.Ctx) error {
	id, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	profile, err := h.svc.GetProfile(id)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	if profile == nil {
		return c.Status(404).JSON(dto.Error("user profile not found"))
	}
	return c.JSON(dto.Success(profile))
}

func (h *UserHandler) UpdateProfile(c *fiber.Ctx) error {
	id, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	var req dto.UpdateProfileRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(dto.Error("invalid request body"))
	}

	profile, err := h.svc.UpdateProfile(id, &req)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(profile))
}

func (h *UserHandler) GetStats(c *fiber.Ctx) error {
	id, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	stats, err := h.svc.GetStats(id)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(stats))
}

func (h *UserHandler) GetFollowers(c *fiber.Ctx) error {
	id, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	page, size := parsePagination(c)
	result, err := h.svc.GetFollowers(id, page, size)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(result))
}

func (h *UserHandler) GetFollowing(c *fiber.Ctx) error {
	id, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	page, size := parsePagination(c)
	result, err := h.svc.GetFollowing(id, page, size)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(result))
}

func (h *UserHandler) FollowUser(c *fiber.Ctx) error {
	followerID, err := uuid.Parse(c.Locals("userID").(string))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}
	followingID, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	if err := h.svc.FollowUser(followerID, followingID); err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(dto.FollowResponse{Following: true}))
}

func (h *UserHandler) UnfollowUser(c *fiber.Ctx) error {
	followerID, err := uuid.Parse(c.Locals("userID").(string))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}
	followingID, err := uuid.Parse(c.Params("id"))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	if err := h.svc.UnfollowUser(followerID, followingID); err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(dto.FollowResponse{Following: false}))
}

func (h *UserHandler) SearchUsers(c *fiber.Ctx) error {
	query := c.Query("q")
	if query == "" {
		return c.Status(400).JSON(dto.Error("query parameter 'q' is required"))
	}

	page, size := parsePagination(c)
	result, err := h.svc.SearchUsers(query, page, size)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(result))
}

func (h *UserHandler) GetMyFollowing(c *fiber.Ctx) error {
	userID, err := uuid.Parse(c.Locals("userID").(string))
	if err != nil {
		return c.Status(400).JSON(dto.Error("invalid user id"))
	}

	ids, err := h.svc.GetFollowingIDs(userID)
	if err != nil {
		return c.Status(500).JSON(dto.Error(err.Error()))
	}
	return c.JSON(dto.Success(ids))
}

func parsePagination(c *fiber.Ctx) (int, int) {
	page, _ := strconv.Atoi(c.Query("page", "1"))
	size, _ := strconv.Atoi(c.Query("size", "20"))
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 100 {
		size = 20
	}
	return page, size
}
