'use client';

import { useEffect, ReactNode } from 'react';
import { useAuthStore } from '@/store/authStore';

interface AuthProviderProps {
  children: ReactNode;
}

export default function AuthProvider({ children }: AuthProviderProps) {
  const checkAuth = useAuthStore((state) => state.checkAuth);

  useEffect(() => {
    // 检查用户认证状态
    checkAuth();
  }, [checkAuth]);

  return <>{children}</>;
}
