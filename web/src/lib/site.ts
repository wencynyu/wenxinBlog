/**
 * 站点绝对 URL（metadataBase / canonical / sitemap / OG 链接共用）。
 * 生产部署时通过 NEXT_PUBLIC_SITE_URL 注入真实域名。
 */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000').replace(
  /\/+$/,
  '',
);
