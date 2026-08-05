import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type {
  User,
  AuthState,
  LoginRequest,
  RegisterRequest,
  PhoneLoginRequest,
  AuthResponse,
} from '@/types/auth';
import * as api from '@/lib/api/auth';
import { setToken, clearToken } from '@/lib/api/client';

interface AuthStore extends AuthState {
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  loginWithPhone: (data: PhoneLoginRequest) => Promise<void>;
  /** 由 AuthResponse 建立会话（登录/手机号/OAuth 三方复用）。 */
  setSession: (response: AuthResponse) => void;
  logout: () => void;
  setUser: (user: User | null) => void;
  setLoading: (loading: boolean) => void;
  checkAuth: () => Promise<void>;
}

// Token存储key
const TOKEN_KEY = 'auth_token';

// 从localStorage获取初始token
const getInitialToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      user: null,
      token: getInitialToken(),
      isAuthenticated: false,
      isLoading: false,

      login: async (credentials: LoginRequest) => {
        set({ isLoading: true });
        try {
          const response = await api.login(credentials);
          get().setSession(response);
        } catch (error: any) {
          set({ isLoading: false });
          throw error;
        }
      },

      setSession: (response: AuthResponse) => {
        const accessToken = response.tokens.accessToken;
        set({
          user: response.user,
          token: accessToken,
          isAuthenticated: true,
          isLoading: false,
        });
        setToken(accessToken);
      },

      loginWithPhone: async (data: PhoneLoginRequest) => {
        set({ isLoading: true });
        try {
          const response = await api.loginWithPhone(data);
          get().setSession(response);
        } catch (error: any) {
          set({ isLoading: false });
          throw error;
        }
      },

      register: async (data: RegisterRequest) => {
        set({ isLoading: true });
        try {
          // 注册（auth-service 不返回 token，需自动登录）
          await api.register(data);
          // 自动登录拿 token
          const loginResp = await api.login({ email: data.email, password: data.password });
          const accessToken = loginResp.tokens.accessToken;
          set({
            user: loginResp.user,
            token: accessToken,
            isAuthenticated: true,
            isLoading: false,
          });
          setToken(accessToken);
        } catch (error: any) {
          set({ isLoading: false });
          throw error;
        }
      },

      logout: () => {
        set({
          user: null,
          token: null,
          isAuthenticated: false,
          isLoading: false,
        });
        clearToken();
      },

      setUser: (user: User | null) => {
        set({ user });
      },

      setLoading: (loading: boolean) => {
        set({ isLoading: loading });
      },

      checkAuth: async () => {
        const token = get().token;
        if (!token) {
          set({ isAuthenticated: false, user: null });
          return;
        }
        // MVP: token 存在即视为已认证（login 时已验证凭证）。
        // 后续可加 GET /api/v1/auth/me 做服务端 token 验证。
        set({ isAuthenticated: true });
      },
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
