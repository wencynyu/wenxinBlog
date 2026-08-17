import '../global.css';

import { useEffect, useState } from 'react';
import { Stack, router, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as SplashScreen from 'expo-splash-screen';
import { useAuthStore } from '@/store/authStore';
import { setUnauthorizedHandler } from '@/lib/api/client';

// 启动画面保持到会话恢复完成（isLoading 变 false）再隐藏
void SplashScreen.preventAutoHideAsync();

/** 认证门：未登录访问受保护区域 → 登录页；已登录停留在 (auth) → (tabs)。 */
function useAuthGate() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isLoading = useAuthStore((s) => s.isLoading);
  const segments = useSegments();

  useEffect(() => {
    if (isLoading) return;
    const inAuthGroup = segments[0] === '(auth)';
    if (!isAuthenticated && !inAuthGroup) {
      router.replace('/(auth)/login');
    } else if (isAuthenticated && inAuthGroup) {
      router.replace('/(tabs)');
    }
  }, [isAuthenticated, isLoading, segments]);
}

export default function RootLayout() {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, retry: 1 },
        },
      }),
  );
  const bootstrap = useAuthStore((s) => s.bootstrap);
  const isLoading = useAuthStore((s) => s.isLoading);

  useAuthGate();

  // 启动恢复会话：先等 zustand persist 从 AsyncStorage 水化，再用 SecureStore token 验证
  useEffect(() => {
    (async () => {
      if (!useAuthStore.persist.hasHydrated()) {
        await useAuthStore.persist.rehydrate();
      }
      await bootstrap();
    })();
  }, [bootstrap]);

  // 401 统一处理：client 拦截器已清 SecureStore token，这里清内存态并回登录页
  useEffect(() => {
    setUnauthorizedHandler(() => {
      useAuthStore.getState().resetSession();
      router.replace('/(auth)/login');
    });
    return () => setUnauthorizedHandler(() => undefined);
  }, []);

  useEffect(() => {
    if (!isLoading) void SplashScreen.hideAsync();
  }, [isLoading]);

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <StatusBar style="auto" />
        <Stack>
          <Stack.Screen name="(auth)" options={{ headerShown: false }} />
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="posts/[id]/index" options={{ title: '博文详情' }} />
          <Stack.Screen name="posts/[id]/edit" options={{ title: '编辑博文' }} />
          <Stack.Screen name="posts/new" options={{ title: '写博文' }} />
          <Stack.Screen name="search" options={{ headerShown: false }} />
          <Stack.Screen name="user/[id]" options={{ title: '用户主页' }} />
          <Stack.Screen name="settings" options={{ title: '设置' }} />
        </Stack>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
