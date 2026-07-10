'use client';

import Link from 'next/link';
import { IconStarStroked, IconClock, IconLikeHeart } from '@douyinfe/semi-icons';
import { useQuery } from '@tanstack/react-query';
import { getTrendingTags } from '@/lib/api/search';
import { getTrendingPosts } from '@/lib/api/recommend';

const MOCK_TAGS = [
  'React',
  'Next.js',
  'TypeScript',
  'Go',
  'Docker',
  '微服务',
  'TailwindCSS',
  'PostgreSQL',
  'Redis',
  'Kubernetes',
];

const MOCK_TRENDING = [
  { id: '1', title: 'Next.js 14 App Router 完全指南', viewsCount: 5280 },
  { id: '2', title: 'React Server Components 深度解析', viewsCount: 4120 },
  { id: '3', title: 'Go 微服务最佳实践', viewsCount: 3890 },
  { id: '4', title: 'TypeScript 5.0 新特性一览', viewsCount: 3560 },
  { id: '5', title: 'Docker Compose 开发环境配置', viewsCount: 2980 },
];

export default function Sidebar() {
  const { data: trendingTags } = useQuery({
    queryKey: ['sidebar-trending-tags'],
    queryFn: () => getTrendingTags(10),
    staleTime: 10 * 60 * 1000,
  });

  const { data: trendingPosts } = useQuery({
    queryKey: ['sidebar-trending-posts'],
    queryFn: () => getTrendingPosts(5),
    staleTime: 10 * 60 * 1000,
  });

  const tags = trendingTags && trendingTags.length > 0 ? trendingTags : MOCK_TAGS;
  const posts = trendingPosts && trendingPosts.length > 0 ? trendingPosts : MOCK_TRENDING;

  return (
    <aside className="w-full space-y-6">
      {/* 热门标签 */}
      <div className="bg-surface rounded-xl shadow-card p-5">
        <h5 className="eyebrow mb-4 flex items-center gap-2">
          <IconLikeHeart className="text-accent-500" />
          {'// trending tags'}
        </h5>
        <div className="flex flex-wrap gap-2">
          {tags.map((tag: string) => (
            <Link key={tag} href={`/posts?tag=${encodeURIComponent(tag)}`}>
              <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono bg-primary-50 text-primary-700 hover:bg-primary-100 transition-colors cursor-pointer">
                #{tag}
              </span>
            </Link>
          ))}
        </div>
      </div>

      {/* 热门博文 */}
      <div className="bg-surface rounded-xl shadow-card p-5">
        <h5 className="eyebrow mb-4 flex items-center gap-2">
          <IconStarStroked className="text-accent-500" />
          {'// trending posts'}
        </h5>
        <div className="space-y-3">
          {posts.map((item: any) => (
            <Link
              key={item.id}
              href={`/posts/${item.id}`}
              className="block w-full px-2 -mx-2 py-1 rounded-md hover:bg-canvas transition-colors"
            >
              <div className="text-ink text-sm mb-1 line-clamp-2">{item.title}</div>
              <div className="flex items-center text-ink-faint text-xs font-mono">
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
