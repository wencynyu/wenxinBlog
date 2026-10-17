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
  // 会话凭证是 auth-service 签发的 httpOnly cookie，跨源（3000→8080）必须带凭证
  withCredentials: true,
});

// 这些接口的 401 属于正常业务失败（密码错误/验证码失效/会话过期等），
// 不触发"静默续期 + 清会话 + 跳登录"，交给调用方处理；
// 否则硬跳转会把用户刚填的内容和 Toast 全部丢掉
const AUTH_EXEMPT_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/phone/send-code',
  '/api/v1/auth/phone/login',
  '/api/v1/auth/oauth/exchange',
  '/api/v1/auth/refresh',
  // 匿名访客的 /auth/me 401 是常态，不能把游客硬跳到登录页
  '/api/v1/auth/me',
];

const isAuthExempt = (url?: string): boolean =>
  !!url && AUTH_EXEMPT_PATHS.some((path) => url.includes(path));

// 标记已重放过的请求，防止续期成功后仍 401 时无限重试
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

// 静默续期：refresh token 在 httpOnly cookie 里（path 限定 /api/v1/auth），
// 服务端轮换后会重新 Set-Cookie。失败仅返回 false，由调用方决定会话处置。
async function silentRefresh(): Promise<boolean> {
  try {
    await client.post('/api/v1/auth/refresh');
    return true;
  } catch {
    return false;
  }
}

// 响应拦截器
client.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  async (error: AxiosError<ApiResponse>) => {
    const config = error.config as RetriableConfig | undefined;

    if (error.response) {
      const { status, data } = error.response;

      // 401 未授权：先尝试一次静默续期并重放原请求（仅浏览器端、非豁免路径、未重放过）。
      // 续期失败才清会话跳登录。
      if (
        status === 401 &&
        !isAuthExempt(config?.url) &&
        !config?._retried &&
        typeof window !== 'undefined'
      ) {
        if (await silentRefresh()) {
          config!._retried = true;
          return client(config!);
        }
        // 清 zustand persist（否则整页跳转后水合仍是登录态）；
        // 直接操作 localStorage 以避免 client ↔ authStore 循环依赖
        localStorage.removeItem('auth-storage');
        window.location.href = '/login';
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
