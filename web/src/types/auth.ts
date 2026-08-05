// 认证相关类型
export interface User {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatar?: string;
  bio?: string;
  location?: string;
  website?: string;
  createdAt: string;
  updatedAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}

export interface AuthResponse {
  user: User;
  tokens: {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
  };
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// 手机号验证码登录
export interface PhoneLoginRequest {
  phone: string;
  code: string;
}

// OAuth 中间码兑换结果：login→user+tokens；link→仅 provider
export interface OAuthExchangeResponse {
  outcome: 'login' | 'link';
  user?: User;
  tokens?: { accessToken: string; refreshToken: string; expiresIn: number };
  provider?: string;
}

export interface OAuthLinkResponse {
  authUrl: string;
}

export interface OAuthAccountItem {
  provider: string;
  providerUserId: string;
  linkedAt: string;
}
