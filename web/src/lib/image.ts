/**
 * 判断图片 URL 是否落在自有域名（站点 CDN / 本地开发）内。
 * next.config.js 的 images.remotePatterns 只放行这些域名；
 * 用户粘贴的外链封面图等不在白名单内，需以 unoptimized 直出，
 * 避免 /_next/image 沦为对任意主机的开放代理（SSRF / 带宽滥用）。
 */
export function isManagedImageHost(url: string | null | undefined): boolean {
  if (!url) return false;
  try {
    const { hostname } = new URL(
      url,
      typeof window === 'undefined' ? 'http://localhost' : window.location.origin,
    );
    return (
      hostname === 'localhost' ||
      hostname === '127.0.0.1' ||
      hostname.endsWith('.wenxinblog.com') ||
      hostname === 'wenxinblog.com'
    );
  } catch {
    return false;
  }
}
