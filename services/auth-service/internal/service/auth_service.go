package service

import (
	"context"
	"errors"

	"wenxinblog/auth-service/internal/model"
	"wenxinblog/auth-service/internal/repository"

	"golang.org/x/crypto/bcrypt"
)

var (
	ErrUserExists      = errors.New("user already exists")
	ErrInvalidCredentials = errors.New("invalid email or password")
	ErrUserNotFound     = errors.New("user not found")
)

// AuthServicer defines the interface for auth operations (used for mocking in tests)
type AuthServicer interface {
	Register(ctx context.Context, email, username, password string) (*model.User, error)
	Login(ctx context.Context, email, password string) (*TokenPair, *model.User, error)
	ValidateToken(token string) (*Claims, error)
	RefreshToken(token string) (*TokenPair, error)
	GetUserByID(ctx context.Context, id string) (*model.User, error)
}

type AuthService struct {
	userRepo   repository.UserRepository
	jwtService *JWTService
}

func NewAuthService(userRepo repository.UserRepository, jwtService *JWTService) *AuthService {
	return &AuthService{userRepo: userRepo, jwtService: jwtService}
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
	tokens, err := s.jwtService.GenerateTokenPair(user.ID, []string{"USER"})
	if err != nil {
		return nil, nil, err
	}
	return tokens, user, nil
}

func (s *AuthService) ValidateToken(token string) (*Claims, error) {
	return s.jwtService.ParseToken(token)
}

func (s *AuthService) RefreshToken(token string) (*TokenPair, error) {
	claims, err := s.jwtService.ParseToken(token)
	if err != nil {
		return nil, err
	}
	return s.jwtService.GenerateTokenPair(claims.UserID, claims.Roles)
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
