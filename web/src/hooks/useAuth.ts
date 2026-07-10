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
    mutationFn: () => authApi.logout().then(() => logout()),
    onSuccess: () => {
      router.push('/');
    },
  });
}
