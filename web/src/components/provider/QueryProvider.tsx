'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode, useState } from 'react';

interface QueryProviderProps {
  children: ReactNode;
}

export default function QueryProvider({ children }: QueryProviderProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 0, // 每次挂载都重新请求（最新）；react-query 仍会去重并发，不会带回严格模式双发
            gcTime: 30 * 60 * 1000, // 30分钟（v5: cacheTime -> gcTime）
            refetchOnWindowFocus: false,
            // 401/403/404 重试无意义（401 还会与拦截器的跳转形成竞态），其余错误最多重试一次
            retry: (failureCount, error) => {
              const status = (error as { code?: number })?.code;
              if (status === 401 || status === 403 || status === 404) return false;
              return failureCount < 1;
            },
          },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
