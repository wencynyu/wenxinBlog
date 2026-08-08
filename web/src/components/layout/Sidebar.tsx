'use client';

import Link from 'next/link';
import { Card } from '@douyinfe/semi-ui';
import { IconStarStroked } from '@douyinfe/semi-icons';
import { useTrendingPosts } from '@/hooks/useRecommendations';

export default function Sidebar() {
  // 走 recommendation-service 热门（blog_db 真实信号 × 时间衰减）
  const { data } = useTrendingPosts(5);

  const posts = data ?? [];
  if (posts.length === 0) return null;

  return (
    <aside className="w-full space-y-6">
      <Card bodyStyle={{ padding: 20 }}>
        <h5 className="eyebrow mb-4 flex items-center gap-2">
          <IconStarStroked className="text-accent-500" />
          {'// popular posts'}
        </h5>
        <div className="space-y-3">
          {posts.map((post, index) => (
            <Link
              key={post.id}
              href={`/posts/${post.id}`}
              className="block w-full px-2 -mx-2 py-1 rounded-md hover:bg-canvas transition-colors no-underline"
            >
              <div className="flex items-start gap-2">
                <span
                  className={`text-sm font-mono font-bold flex-shrink-0 ${index < 3 ? 'text-accent-500' : 'text-ink-faint'}`}
                >
                  {index + 1}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="text-ink text-sm line-clamp-2">{post.title}</div>
                  <div className="text-ink-faint text-xs font-mono mt-0.5">
                    {post.likeCount || 0} 赞 · {post.commentCount || 0} 评论
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </Card>
    </aside>
  );
}
