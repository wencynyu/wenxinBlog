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

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      user: null,
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

      // 会话凭证在 auth-service 签发的 httpOnly cookie 里，前端不再持有 token；
      // 登录响应不含 roles（无法 decode token），后台拉一次 /auth/me 补齐
      setSession: (response: AuthResponse) => {
        set({
          user: response.user,
          isAuthenticated: true,
          isLoading: false,
        });
        get().checkAuth();
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
          // 自动登录建立会话（cookie 由登录响应 Set-Cookie 写入）
          const loginResp = await api.login({ email: data.email, password: data.password });
          get().setSession(loginResp);
        } catch (error: any) {
          set({ isLoading: false });
          throw error;
        }
      },

      logout: () => {
        set({
          user: null,
          isAuthenticated: false,
          isLoading: false,
        });
      },

      setUser: (user: User | null) => {
        set({ user });
      },

      setLoading: (loading: boolean) => {
        set({ isLoading: loading });
      },

      // 服务端会话校验：GET /auth/me 带 httpOnly cookie，有效则返回最新
      // user（含 roles），无效（未登录/过期）则回归匿名态。匿名 401 是常态。
      checkAuth: async () => {
        try {
          const user = await api.getCurrentUser();
          set({ user, isAuthenticated: true, isLoading: false });
        } catch {
          set({ user: null, isAuthenticated: false, isLoading: false });
        }
      },
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
