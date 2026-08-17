import axios, { AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import { API_BASE_URL } from '@/constants';
import { getToken, clearToken } from '@/lib/storage';

// 统一错误形状（拦截器 reject 的都是这个）
export interface ApiError {
  code: number;
  message: string;
  data?: unknown;
}

/** 从任意 thrown 值里取用户可读的错误信息（拦截器 reject 的是 ApiError，非 API 路径可能是 Error）。 */
export function getErrorMessage(error: unknown, fallback = '请求失败'): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message) return message;
  }
  return fallback;
}

// 创建 axios 实例（axios.create 是官方用法，非 zustand 的 create）
// eslint-disable-next-line import/no-named-as-default-member
const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 401 的后续处理（清 store + 跳登录）由 app 层注册，避免 client ↔ store 循环依赖
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

// 请求拦截器：异步从 SecureStore（Keychain）读 token 附加 Bearer
client.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await getToken();
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：解包 ApiResponse{code,message,data} 信封；401 清 token 并通知 app 层
client.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  async (error: AxiosError<ApiError>) => {
    if (error.response) {
      const { status, data } = error.response;

      if (status === 401) {
        await clearToken();
        onUnauthorized?.();
      }

      return Promise.reject({
        code: data?.code || status,
        message: data?.message || '请求失败',
        data: data?.data,
      } satisfies ApiError);
    }

    if (error.code === 'ECONNABORTED') {
      return Promise.reject({ code: 408, message: '请求超时' } satisfies ApiError);
    }

    return Promise.reject({ code: 500, message: '网络错误' } satisfies ApiError);
  },
);

export default client;
