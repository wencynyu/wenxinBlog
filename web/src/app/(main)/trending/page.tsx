'use client';

import Link from 'next/link';
import { Tag } from '@douyinfe/semi-ui';
import MainLayout from '@/components/layout/MainLayout';
import { useQuery } from 'react-query';
import { getTrendingPosts } from '@/lib/api/recommend';
import { getTrendingTags } from '@/lib/api/search';

const MOCK_TAGS = ['React', 'Next.js', 'TypeScript', 'Go', 'Docker', '微服务', 'TailwindCSS', 'PostgreSQL', 'Redis', 'Kubernetes', 'Rust', 'Python', 'AI', 'LLM', 'Kubernetes'];

const MOCK_TRENDING = [
  { id: '1', title: '2026年前端技术趋势预测', likesCount: 456, viewsCount: 8920, author: { displayName: '前端观察者', username: 'frontend-observer' } },
  { id: '2', title: '从零到一搭建 CI/CD 流水线', likesCount: 342, viewsCount: 6780, author: { displayName: 'DevOps工程师', username: 'devops-eng' } },
  { id: '3', title: 'React vs Vue vs Svelte 对比评测', likesCount: 289, viewsCount: 5640, author: { displayName: '前端达人', username: 'frontend-master' } },
  { id: '4', title: 'Go 语言并发编程深入理解', likesCount: 267, viewsCount: 5230, author: { displayName: 'Go专家', username: 'go-expert' } },
  { id: '5', title: 'Docker 多阶段构建最佳实践', likesCount: 234, viewsCount: 4560, author: { displayName: 'Docker布道师', username: 'docker-evangelist' } },
  { id: '6', title: 'TypeScript 高级类型体操指南', likesCount: 213, viewsCount: 4120, author: { displayName: 'TS布道者', username: 'ts-preacher' } },
  { id: '7', title: 'PostgreSQL 性能调优实战', likesCount: 198, viewsCount: 3890, author: { displayName: 'DBA小王', username: 'dba-wang' } },
  { id: '8', title: 'Next.js App Router vs Pages Router', likesCount: 187, viewsCount: 3670, author: { displayName: 'Next.js爱好者', username: 'nextjs-fan' } },
  { id: '9', title: '微服务架构中的服务通信模式', likesCount: 176, viewsCount: 3450, author: { displayName: '架构师老李', username: 'architect-li' } },
  { id: '10', title: 'Tailwind CSS v4 迁移指南', likesCount: 165, viewsCount: 3230, author: { displayName: 'CSS魔法师', username: 'css-magician' } },
];

export default function TrendingPage() {
  const { data: trendingPosts } = useQuery(
    'trending-posts-page',
    () => getTrendingPosts(20)
  );

  const { data: trendingTags } = useQuery(
    'trending-tags-page',
    () => getTrendingTags(20),
    { staleTime: 10 * 60 * 1000 }
  );

  const tags = trendingTags && trendingTags.length > 0 ? trendingTags : MOCK_TAGS;
  const posts = trendingPosts && trendingPosts.length > 0 ? trendingPosts : MOCK_TRENDING;
  const isLoading = !trendingPosts;
  const useMock = !trendingPosts || trendingPosts.length === 0;

  return (
    <MainLayout>
      <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">热门博文</h2>

      {useMock && <p className="text-gray-400 text-sm mb-4">（演示数据 — 连接后端后显示真实内容）</p>}

      {/* 热门标签 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-6 dark:bg-gray-800 dark:border-gray-700">
        <h3 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">热门标签</h3>
        <div className="flex flex-wrap gap-2">
          {tags.map((tag: string) => (
            <Link key={tag} href={`/posts?tag=${encodeURIComponent(tag)}`}>
              <Tag color="cyan" size="large" className="cursor-pointer hover:opacity-80 transition-opacity">
                #{tag}
              </Tag>
            </Link>
          ))}
        </div>
      </div>

      {/* 热门博文列表 */}
      {isLoading ? (
        <div className="space-y-4">
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-5 animate-pulse">
              <div className="h-5 bg-gray-200 rounded w-3/4 mb-3" />
              <div className="h-4 bg-gray-100 rounded w-full mb-1" />
              <div className="h-4 bg-gray-100 rounded w-2/3" />
            </div>
          ))}
        </div>
      ) : (
        <div className="space-y-3">
          {posts.map((post: any, index: number) => (
            <Link
              key={post.id}
              href={`/posts/${post.id}`}
              className="flex items-center gap-4 bg-white rounded-lg border border-gray-200 p-4 hover:border-gray-300 hover:shadow-sm transition-all dark:bg-gray-800 dark:border-gray-700"
            >
              <span className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${
                index < 3 ? 'text-sky-500' : 'text-gray-300'
              }`}>
                {index + 1}
              </span>
              <div className="flex-1 min-w-0">
                <h3 className="font-semibold text-gray-900 dark:text-gray-100 line-clamp-1 hover:text-sky-500 transition-colors">
                  {post.title}
                </h3>
                <div className="flex items-center gap-3 mt-1 text-gray-400 text-sm">
                  <span>{post.author?.displayName || post.author?.username}</span>
                  <span>{post.likesCount} 赞</span>
                  <span>{post.viewsCount} 阅读</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </MainLayout>
  );
}
