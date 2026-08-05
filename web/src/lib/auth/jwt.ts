// JWT access token payload 解码（仅读 payload，不验签——签名由网关验证）。
// 后端 GenerateTokenPair 把 roles/permissions 写入 claims，这是前端判断管理员身份的来源。

interface JwtPayload {
  userId?: string;
  roles?: string[];
  permissions?: string[];
  tokenType?: string;
  exp?: number;
}

export function decodeAccessToken(token: string | null | undefined): JwtPayload | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(b64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function getTokenRoles(token: string | null | undefined): string[] {
  return decodeAccessToken(token)?.roles ?? [];
}

export function getTokenPermissions(token: string | null | undefined): string[] {
  return decodeAccessToken(token)?.permissions ?? [];
}
