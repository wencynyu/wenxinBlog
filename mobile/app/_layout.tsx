import { Stack } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';

const queryClient = new QueryClient();

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(auth)" options={{ headerShown: false }} />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="posts/[id]" options={{ headerShown: true, title: '博文详情' }} />
        <Stack.Screen name="posts/new" options={{ headerShown: true, title: '写博文' }} />
        <Stack.Screen name="user/[id]" options={{ headerShown: true }} />
        <Stack.Screen name="search" options={{ headerShown: true, title: '搜索' }} />
        <Stack.Screen name="settings" options={{ headerShown: true, title: '设置' }} />
      </Stack>
    </QueryClientProvider>
  );
}
