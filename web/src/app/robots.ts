import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/site';

/**
 * robots.txt：个人页面与写操作页面不收录，公开内容页放行。
 * 文章详情/列表是 SEO 主要目标（见 sitemap.ts）。
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/admin', '/editor', '/settings', '/auth', '/feed', '/login', '/register'],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
