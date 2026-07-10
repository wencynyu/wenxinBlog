import { cookies } from 'next/headers';
import type { ApiResponse } from '@/types/common';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * 服务端取数助手（用于 React Server Component / generateMetadata）。
 * 网关返回 ApiResponse<T>，这里解包出 .data。
 *
 * 鉴权：当前 token 存于客户端 localStorage，服务端读不到 cookie，故目前仅支持
 * 公开接口。鉴权相关的服务端取数需要先把 token 写入 cookie（见文档 TODO）。
 */
export async function serverGet<T>(path: string): Promise<T> {
  const token = cookies().get('auth_token')?.value;
  const res = await fetch(`${API_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: 'no-store',
  });
  if (!res.ok) {
    throw new Error(`serverGet ${res.status} on ${path}`);
  }
  const json = (await res.json()) as ApiResponse<T>;
  return json.data;
}
