import Link from 'next/link';
import type { Metadata } from 'next';
import MainLayout from '@/components/layout/MainLayout';
import EmptyState from '@/components/common/EmptyState';
import { serverGet } from '@/lib/api/server';
import type { TrendingPost } from '@/lib/api/recommend';

// 后端在请求时才可访问；不在构建期预取。
export const dynamic = 'force-dynamic';

export const metadata: Metadata = {
  title: '热门博文 - WenxinBlog',
  description: 'WenxinBlog 热门技术博文与标签',
};

export default async function TrendingPage() {
  let posts: TrendingPost[] = [];
  let tags: string[] = [];
  try {
    [posts, tags] = await Promise.all([
      serverGet<TrendingPost[]>('/api/v1/recommend/trending?limit=20'),
      serverGet<string[]>('/api/v1/search/trending/tags?limit=20'),
    ]);
  } catch {
    // 后端不可用 → 走空状态
  }

  return (
    <MainLayout>
      <p className="eyebrow mb-3">{'// trending'}</p>
      <h2 className="font-serif text-2xl font-semibold text-ink mb-6">热门博文</h2>

      {/* 热门标签 */}
      {tags.length > 0 && (
        <div className="bg-surface rounded-xl shadow-card p-5 mb-6">
          <h3 className="eyebrow mb-3">{'// tags'}</h3>
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <Link key={tag} href={`/posts?tag=${encodeURIComponent(tag)}`}>
                <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono bg-primary-50 text-primary-700 hover:bg-primary-100 transition-colors cursor-pointer">
                  #{tag}
                </span>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* 热门博文列表 */}
      {posts.length === 0 ? (
        <EmptyState title="暂无热门内容" description="后端服务暂不可用，请稍后再试" />
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
                  <span>{post.author?.displayName || post.author?.username}</span>
                  <span>{post.likeCount} 赞</span>
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
