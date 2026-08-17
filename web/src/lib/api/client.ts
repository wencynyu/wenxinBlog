import axios, { AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios';

// API响应基础类型
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// 分页响应类型
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

// 创建axios实例
const client = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Token存储key
const TOKEN_KEY = 'auth_token';
// 同时写入 cookie，供 Server Component / generateMetadata 读取（server.ts 读 cookie）。
// 非 httpOnly：与 localStorage 同等 XSS 暴露面；httpOnly 升级需要服务端 set-cookie，见 TODO。
const COOKIE_KEY = 'auth_token';
const COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7天

// 获取token（客户端从 localStorage 读，保持同步快速）
const getToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
};

// 设置token：同时写 localStorage（客户端用）与 cookie（服务端用）
export const setToken = (token: string): void => {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, token);
  document.cookie = `${COOKIE_KEY}=${encodeURIComponent(token)}; path=/; SameSite=Lax; max-age=${COOKIE_MAX_AGE}`;
};

// 清除token：同时清 localStorage 与 cookie
export const clearToken = (): void => {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  document.cookie = `${COOKIE_KEY}=; path=/; SameSite=Lax; max-age=0`;
};

// 请求拦截器
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  },
);

// 这些接口的 401 属于正常业务失败（密码错误/验证码失效等），不触发"清会话+跳登录"，
// 交给表单层展示错误；否则硬跳转会把用户刚填的内容和 Toast 全部丢掉
const AUTH_EXEMPT_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/phone/send-code',
  '/api/v1/auth/phone/login',
  '/api/v1/auth/oauth/exchange',
];

const isAuthExempt = (url?: string): boolean =>
  !!url && AUTH_EXEMPT_PATHS.some((path) => url.includes(path));

// 响应拦截器
client.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  (error: AxiosError<ApiResponse>) => {
    if (error.response) {
      const { status, data } = error.response;

      // 401 未授权 - 清除本地会话并跳转登录。
      // 除 auth_token 外还需清 zustand persist（否则整页跳转后水合仍是登录态）；
      // 直接操作 localStorage 以避免 client ↔ authStore 循环依赖
      if (status === 401 && !isAuthExempt(error.config?.url)) {
        clearToken();
        if (typeof window !== 'undefined') {
          localStorage.removeItem('auth-storage');
          window.location.href = '/login';
        }
      }

      // 返回API错误信息
      return Promise.reject({
        code: data?.code || status,
        message: data?.message || '请求失败',
        data: data?.data,
      });
    }

    // 网络错误
    if (error.code === 'ECONNABORTED') {
      return Promise.reject({
        code: 408,
        message: '请求超时',
      });
    }

    return Promise.reject({
      code: 500,
      message: '网络错误',
    });
  },
);

export default client;
