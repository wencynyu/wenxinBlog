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

// 获取token
const getToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
};

// 设置token
export const setToken = (token: string): void => {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, token);
};

// 清除token
export const clearToken = (): void => {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
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
  }
);

// 响应拦截器
client.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  (error: AxiosError<ApiResponse>) => {
    if (error.response) {
      const { status, data } = error.response;

      // 401 未授权 - 清除token并跳转登录
      if (status === 401) {
        clearToken();
        if (typeof window !== 'undefined') {
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
  }
);

export default client;
