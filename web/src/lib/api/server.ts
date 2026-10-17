import { cookies } from 'next/headers';
import type { ApiResponse } from '@/types/common';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * 服务端取数助手（用于 React Server Component / generateMetadata）。
 * 网关返回 ApiResponse<T>，这里解包出 .data。
 *
 * 鉴权：读 auth-service 签发的 httpOnly 会话 cookie（服务端可读）并透传 Bearer。
 */
export async function serverGet<T>(path: string): Promise<T> {
  const token = cookies().get('access_token')?.value;
  const res = await fetch(`${API_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: 'no-store',
    // 网关挂起时防止 SSR 无限等待拖死路由渲染
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) {
    throw new Error(`serverGet ${res.status} on ${path}`);
  }
  const json = (await res.json()) as ApiResponse<T>;
  return json.data;
}
