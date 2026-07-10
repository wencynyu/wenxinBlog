'use client';

import Link from 'next/link';
import { IconStarStroked, IconClock, IconLikeHeart } from '@douyinfe/semi-icons';
import { Tag } from '@douyinfe/semi-ui';
import { useQuery } from 'react-query';
import { getTrendingTags } from '@/lib/api/search';
import { getTrendingPosts } from '@/lib/api/recommend';

const MOCK_TAGS = ['React', 'Next.js', 'TypeScript', 'Go', 'Docker', '微服务', 'TailwindCSS', 'PostgreSQL', 'Redis', 'Kubernetes'];

const MOCK_TRENDING = [
  { id: '1', title: 'Next.js 14 App Router 完全指南', viewsCount: 5280 },
  { id: '2', title: 'React Server Components 深度解析', viewsCount: 4120 },
  { id: '3', title: 'Go 微服务最佳实践', viewsCount: 3890 },
  { id: '4', title: 'TypeScript 5.0 新特性一览', viewsCount: 3560 },
  { id: '5', title: 'Docker Compose 开发环境配置', viewsCount: 2980 },
];

export default function Sidebar() {
  const { data: trendingTags } = useQuery(
    'sidebar-trending-tags',
    () => getTrendingTags(10),
    { staleTime: 10 * 60 * 1000 }
  );

  const { data: trendingPosts } = useQuery(
    'sidebar-trending-posts',
    () => getTrendingPosts(5),
    { staleTime: 10 * 60 * 1000 }
  );

  const tags = trendingTags && trendingTags.length > 0 ? trendingTags : MOCK_TAGS;
  const posts = trendingPosts && trendingPosts.length > 0 ? trendingPosts : MOCK_TRENDING;

  return (
    <aside className="w-full space-y-6">
      {/* 热门标签 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 dark:bg-gray-800 dark:border-gray-700">
        <h5 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center">
          <IconLikeHeart className="mr-2 text-rose-500" />
          热门标签
        </h5>
        <div className="flex flex-wrap gap-2">
          {tags.map((tag: string) => (
            <Link key={tag} href={`/posts?tag=${encodeURIComponent(tag)}`}>
              <Tag color="cyan" className="cursor-pointer hover:opacity-80 transition-opacity">
                #{tag}
              </Tag>
            </Link>
          ))}
        </div>
      </div>

      {/* 热门博文 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 dark:bg-gray-800 dark:border-gray-700">
        <h5 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center">
          <IconStarStroked className="mr-2 text-amber-500" />
          热门博文
        </h5>
        <div className="space-y-3">
          {posts.map((item: any) => (
            <Link
              key={item.id}
              href={`/posts/${item.id}`}
              className="block w-full px-2 -mx-2 py-1 rounded hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            >
              <div className="text-gray-700 dark:text-gray-300 text-sm mb-1 line-clamp-2">
                {item.title}
              </div>
              <div className="flex items-center text-gray-400 text-xs">
                <IconClock size="small" />
                <span className="ml-1">{item.viewsCount?.toLocaleString() || 0} 阅读</span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </aside>
  );
}
