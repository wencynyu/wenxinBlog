import client from './client';
import type {
  User,
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  PhoneLoginRequest,
  OAuthExchangeResponse,
  OAuthLinkResponse,
  OAuthAccountItem,
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

// ============ 手机号验证码登录 ============

/** 发送短信验证码（成功/限流都返回统一消息，不泄露号码是否注册） */
export async function sendPhoneCode(phone: string): Promise<void> {
  await client.post('/api/v1/auth/phone/send-code', { phone });
}

/** 手机号 + 验证码登录 */
export async function loginWithPhone(data: PhoneLoginRequest): Promise<AuthResponse> {
  const response: ApiResponse<AuthResponse> = await client.post('/api/v1/auth/phone/login', data);
  return response.data;
}

// ============ 第三方登录（OAuth） ============

/** OAuth 登录/绑定发起 URL（顶层导航到网关）。 */
export function oauthLoginURL(provider: string): string {
  const base = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
  return `${base}/api/v1/auth/oauth/${provider}`;
}

/** 用一次性中间码兑换登录态（login）或确认绑定（link）。 */
export async function exchangeOAuthCode(code: string): Promise<OAuthExchangeResponse> {
  const response: ApiResponse<OAuthExchangeResponse> = await client.post(
    '/api/v1/auth/oauth/exchange',
    { code },
  );
  return response.data;
}

/** 已登录用户发起第三方绑定，返回 provider 授权 URL。 */
export async function linkOAuth(provider: string): Promise<OAuthLinkResponse> {
  const response: ApiResponse<OAuthLinkResponse> = await client.post(
    `/api/v1/account/oauth/${provider}/link`,
  );
  return response.data;
}

/** 解绑第三方账号 */
export async function unlinkOAuth(provider: string): Promise<void> {
  await client.delete(`/api/v1/account/oauth/${provider}`);
}

/** 列出当前用户已绑定的第三方账号 */
export async function listOAuthLinks(): Promise<OAuthAccountItem[]> {
  const response: ApiResponse<{ items: OAuthAccountItem[] }> =
    await client.get('/api/v1/account/oauth');
  return response.data?.items || [];
}
