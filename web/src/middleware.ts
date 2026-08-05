// 服务端路由守卫：/admin/* 仅放行 token roles 含 admin 的请求（粗筛，decode 不验签）。
// 细粒度由后端 role:manage 兜底；客户端 layout 再做 useIsAdmin 二次确认。
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

function tokenRoles(token: string): string[] {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return [];
    const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf-8'));
    return payload?.roles ?? [];
  } catch {
    return [];
  }
}

export function middleware(request: NextRequest) {
  const token = request.cookies.get('auth_token')?.value;
  if (!token || !tokenRoles(token).includes('admin')) {
    return NextResponse.redirect(new URL('/', request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/admin/:path*'],
};
