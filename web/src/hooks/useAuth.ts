import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import * as authApi from '@/lib/api/auth';
import type { LoginRequest, RegisterRequest } from '@/types/auth';

export function useLogin() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  return useMutation({
    mutationFn: (credentials: LoginRequest) => login(credentials),
    onSuccess: () => {
      router.push('/');
    },
  });
}

export function useRegister() {
  const router = useRouter();
  const register = useAuthStore((state) => state.register);

  return useMutation({
    mutationFn: (data: RegisterRequest) => register(data),
    onSuccess: () => {
      router.push('/');
    },
  });
}

export function useLogout() {
  const router = useRouter();
  const logout = useAuthStore((state) => state.logout);

  return useMutation({
    // 服务端登出失败（如 token 已过期的 401）不应阻断本地会话清理，
    // 否则 persist 里的 user/token 全部残留，界面仍是登录态
    mutationFn: async () => {
      try {
        await authApi.logout();
      } finally {
        logout();
      }
    },
    onSuccess: () => {
      router.push('/');
    },
  });
}

// 是否管理员：roles 来自 /auth/me（authStore.user.roles）。
// httpOnly cookie 化后前端无法 decode token，这是唯一角色来源。
export function useIsAdmin(): boolean {
  const roles = useAuthStore((state) => state.user?.roles);
  return roles?.includes('admin') ?? false;
}
