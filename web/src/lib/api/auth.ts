import client from './client';
import type {
  User,
  LoginRequest,
  RegisterRequest,
  AuthResponse,
} from '@/types/auth';
import type { ApiResponse } from '@/types/common';

/**
 * 用户登录
 */
export async function login(data: LoginRequest): Promise<AuthResponse> {
  const response: ApiResponse<AuthResponse> = await client.post('/api/v1/auth/login', data);
  return response.data;
}

/**
 * 用户注册
 */
export async function register(data: RegisterRequest): Promise<AuthResponse> {
  const response: ApiResponse<AuthResponse> = await client.post('/api/v1/auth/register', data);
  return response.data;
}

/**
 * 获取当前用户信息
 */
export async function getCurrentUser(): Promise<User> {
  const response: ApiResponse<User> = await client.get('/api/v1/auth/me');
  return response.data;
}

/**
 * 退出登录
 */
export async function logout(): Promise<void> {
  await client.post('/api/v1/auth/logout');
}

/**
 * 刷新Token
 */
export async function refreshToken(refreshToken: string): Promise<AuthResponse> {
  const response: ApiResponse<AuthResponse> = await client.post('/api/v1/auth/refresh', {
    refreshToken,
  });
  return response.data;
}
