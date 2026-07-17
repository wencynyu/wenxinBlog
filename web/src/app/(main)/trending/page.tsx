'use client';

import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import EmptyState from '@/components/common/EmptyState';
import { useTrendingPosts } from '@/hooks/useRecommendations';

export default function TrendingPage() {
  const { data, isLoading: loading } = useTrendingPosts(20);
  const posts = data ?? [];

  if (loading) {
    return (
      <MainLayout>
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-surface rounded-xl p-4 animate-pulse h-16" />
          ))}
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <p className="eyebrow mb-3">{'// trending'}</p>
      <h2 className="font-serif text-2xl font-semibold text-ink mb-6">热门博文</h2>

      {posts.length === 0 ? (
        <EmptyState title="暂无热门内容" description="还没有发布任何博文" />
      ) : (
        <div className="space-y-3">
          {posts.map((post, index) => (
            <Link
              key={post.id}
              href={`/posts/${post.id}`}
              className="flex items-center gap-4 bg-surface rounded-xl shadow-card hover:shadow-card-hover p-4 transition-all"
            >
              <span
                className={`text-2xl font-bold w-8 text-center flex-shrink-0 font-mono ${
                  index < 3 ? 'text-accent-500' : 'text-ink-faint'
                }`}
              >
                {index + 1}
              </span>
              <div className="flex-1 min-w-0">
                <h3 className="font-semibold text-ink line-clamp-1">{post.title}</h3>
                <div className="flex items-center gap-3 mt-1 text-ink-faint text-sm font-mono">
                  <span>{post.author?.displayName || post.author?.username || '匿名'}</span>
                  <span>{post.viewsCount || 0} 浏览</span>
                  <span>{post.likeCount || 0} 赞</span>
                  <span>{post.commentCount || 0} 评论</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </MainLayout>
  );
}
