'use client';

import Link from 'next/link';
import { Card, Typography, Skeleton } from '@douyinfe/semi-ui';
import MainLayout from '@/components/layout/MainLayout';
import EmptyState from '@/components/common/EmptyState';
import PageHeader from '@/components/common/PageHeader';
import { useTrendingPosts } from '@/hooks/useRecommendations';

export default function TrendingPage() {
  const { data, isLoading: loading } = useTrendingPosts(20);
  const posts = data ?? [];

  if (loading) {
    return (
      <MainLayout>
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Card key={i} bodyStyle={{ padding: 16 }}>
              <Skeleton.Title style={{ width: '60%', marginBottom: 8 }} />
              <Skeleton.Paragraph style={{ width: '40%' }} />
            </Card>
          ))}
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <PageHeader eyebrow="// trending" title="热门博文" />

      {posts.length === 0 ? (
        <EmptyState title="暂无热门内容" description="还没有发布任何博文" />
      ) : (
        <div className="space-y-3">
          {posts.map((post, index) => (
            <Link
              key={post.id}
              href={`/posts/${post.id}`}
              style={{ display: 'block', textDecoration: 'none' }}
            >
              <Card shadows="hover" bodyStyle={{ padding: 16 }}>
                <div className="flex items-center gap-4">
                  <Typography.Text
                    strong
                    className={`flex-shrink-0 ${index < 3 ? 'text-accent-500' : 'text-ink-faint'}`}
                    style={{ fontSize: 22, width: 28, textAlign: 'center' }}
                  >
                    {index + 1}
                  </Typography.Text>
                  <div className="flex-1 min-w-0">
                    <Typography.Text className="font-semibold line-clamp-1">
                      {post.title}
                    </Typography.Text>
                    <div className="flex items-center gap-3 mt-1 font-mono">
                      <Typography.Text type="tertiary" size="small">
                        {post.author?.displayName || post.author?.username || '匿名'}
                      </Typography.Text>
                      <Typography.Text type="tertiary" size="small">
                        {post.viewsCount || 0} 浏览
                      </Typography.Text>
                      <Typography.Text type="tertiary" size="small">
                        {post.likeCount || 0} 赞
                      </Typography.Text>
                      <Typography.Text type="tertiary" size="small">
                        {post.commentCount || 0} 评论
                      </Typography.Text>
                    </div>
                  </div>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </MainLayout>
  );
}
