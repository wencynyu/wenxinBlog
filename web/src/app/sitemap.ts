import type { MetadataRoute } from 'next';
import { serverGet } from '@/lib/api/server';
import { SITE_URL } from '@/lib/site';
import type { Post } from '@/types/post';
import type { PaginatedResponse } from '@/types/common';

// 按请求生成（构建期不请求网关，CI 无后端也能 build）；网关不可用时退化为静态条目
export const dynamic = 'force-dynamic';

/** 安全上限：sitemap 单文件 5 万条，这里远用不到，防御性截断。 */
const MAX_PAGES = 50;
const PAGE_SIZE = 100;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticEntries: MetadataRoute.Sitemap = [
    { url: `${SITE_URL}/`, changeFrequency: 'daily', priority: 1 },
    { url: `${SITE_URL}/posts`, changeFrequency: 'daily', priority: 0.9 },
    { url: `${SITE_URL}/trending`, changeFrequency: 'hourly', priority: 0.8 },
  ];

  try {
    const posts: Post[] = [];
    let page = 1;
    let totalPages = 1;
    do {
      const res = await serverGet<PaginatedResponse<Post>>(
        // status=published：草稿/下架文章不进 sitemap（否则爬虫抓到软 404 损害站点质量分）
        `/api/v1/posts?page=${page}&pageSize=${PAGE_SIZE}&status=published`,
      );
      posts.push(...res.items);
      totalPages = Math.min(res.totalPages, MAX_PAGES);
      page += 1;
    } while (page <= totalPages);

    return [
      ...staticEntries,
      ...posts.map((p) => ({
        url: `${SITE_URL}/posts/${p.id}`,
        lastModified: new Date(p.updatedAt || p.publishedAt || p.createdAt),
        changeFrequency: 'weekly' as const,
        priority: 0.7,
      })),
    ];
  } catch {
    // 网关不可用/超时：只返回静态入口，不因 SEO 装置影响站点可用性
    return staticEntries;
  }
}
