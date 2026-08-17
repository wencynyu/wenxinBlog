'use client';

import { useEffect, useRef, ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';

interface AuthProviderProps {
  children: ReactNode;
}

export default function AuthProvider({ children }: AuthProviderProps) {
  const checkAuth = useAuthStore((state) => state.checkAuth);
  const userId = useAuthStore((state) => state.user?.id ?? null);
  const queryClient = useQueryClient();
  const prevUserId = useRef<string | null>(null);
  const mounted = useRef(false);

  useEffect(() => {
    // 检查用户认证状态
    checkAuth();
  }, [checkAuth]);

  // 账号切换（登出 / 换号登录）时清空全部查询缓存，避免前一账号的
  // 个性化数据（推荐流、个人页、admin 列表）串到下一账号。
  // null → id 的登录场景不清：匿名缓存无账号归属，且 staleTime 0 会立即重取
  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      prevUserId.current = userId;
      return;
    }
    if (prevUserId.current !== null && prevUserId.current !== userId) {
      prevUserId.current = userId;
      queryClient.clear();
    } else {
      prevUserId.current = userId;
    }
  }, [userId, queryClient]);

  return <>{children}</>;
}
