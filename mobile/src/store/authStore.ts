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
import { getToken, setToken, clearToken, zustandAsyncStorage } from '@/lib/storage';
import { AUTH_STORAGE_KEY } from '@/constants';

interface AuthStore extends AuthState {
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  loginWithPhone: (data: PhoneLoginRequest) => Promise<void>;
  /** 由 AuthResponse 建立会话（登录/手机号/OAuth 复用），token 落 SecureStore。 */
  setSession: (response: AuthResponse) => void;
  /** App 启动时恢复会话：SecureStore 有 token 则调 /auth/me 验证并恢复 user。 */
  bootstrap: () => Promise<void>;
  logout: () => Promise<void>;
  /** 仅清内存态（token 已被 client 的 401 处理清掉），供 401 回调使用。 */
  resetSession: () => void;
  setUser: (user: User | null) => void;
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      // 启动即 isLoading，直到 bootstrap 验证完会话才放行路由（见 app/_layout.tsx）
      isLoading: true,

      login: async (credentials: LoginRequest) => {
        set({ isLoading: true });
        try {
          const response = await api.login(credentials);
          set({ isLoading: false });
          setSessionInternal(set, response);
        } catch (error) {
          set({ isLoading: false });
          throw error;
        }
      },

      setSession: (response: AuthResponse) => {
        setSessionInternal(set, response);
      },

      loginWithPhone: async (data: PhoneLoginRequest) => {
        set({ isLoading: true });
        try {
          const response = await api.loginWithPhone(data);
          set({ isLoading: false });
          setSessionInternal(set, response);
        } catch (error) {
          set({ isLoading: false });
          throw error;
        }
      },

      register: async (data: RegisterRequest) => {
        set({ isLoading: true });
        try {
          // 注册（auth-service 不返回 token，需自动登录）
          await api.register(data);
          const loginResp = await api.login({ email: data.email, password: data.password });
          set({ isLoading: false });
          setSessionInternal(set, loginResp);
        } catch (error) {
          set({ isLoading: false });
          throw error;
        }
      },

      bootstrap: async () => {
        const token = await getToken();
        if (!token) {
          set({ user: null, token: null, isAuthenticated: false, isLoading: false });
          return;
        }
        set({ token, isLoading: true });
        try {
          // 用 /auth/me 验证 token 有效性并恢复 user（web 端 MVP 只信 token，这里做实）
          const user = await api.getCurrentUser();
          set({ user, isAuthenticated: true, isLoading: false });
        } catch {
          await clearToken();
          set({ user: null, token: null, isAuthenticated: false, isLoading: false });
        }
      },

      logout: async () => {
        try {
          await api.logout();
        } catch {
          // 服务端登出失败（token 已失效/断网）不阻塞本地登出
        }
        await clearToken();
        set({ user: null, token: null, isAuthenticated: false, isLoading: false });
      },

      resetSession: () => {
        set({ user: null, token: null, isAuthenticated: false, isLoading: false });
      },

      setUser: (user: User | null) => {
        set({ user });
      },
    }),
    {
      name: AUTH_STORAGE_KEY,
      storage: createJSONStorage(() => zustandAsyncStorage),
      // token 只存 SecureStore（见 lib/storage.ts），persist 只缓存非敏感的展示态
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);

function setSessionInternal(
  set: (partial: Partial<AuthStore>) => void,
  response: AuthResponse,
): void {
  const accessToken = response.tokens.accessToken;
  set({
    user: response.user,
    token: accessToken,
    isAuthenticated: true,
    isLoading: false,
  });
  // SecureStore 写入异步进行，不阻塞登录返回
  void setToken(accessToken);
}
