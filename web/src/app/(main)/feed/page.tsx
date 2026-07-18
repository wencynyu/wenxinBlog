'use client';

import { Button } from '@douyinfe/semi-ui';
import { IconPlus } from '@douyinfe/semi-icons';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import RecommendationCard from '@/components/recommend/RecommendationCard';
import { useAuthStore } from '@/store/authStore';
import { useFeedRecommendations } from '@/hooks/useRecommendations';
import { getPosts } from '@/lib/api/posts';

export default function FeedPage() {
  const { user, isAuthenticated } = useAuthStore();
  const hasUser = !!user?.id;

  // 登录用户：个性化推荐流（recommendation-service，基于兴趣的内容相似）
  const recs = useFeedRecommendations(user?.id, { size: 12 });
  // 匿名：最新博文兜底（仅未登录时请求）
  const latest = useQuery({
    queryKey: ['posts', 'feed-anonymous'],
    queryFn: () =>
      getPosts({
        page: 1,
        pageSize: 10,
        status: 'published',
        sortBy: 'createdAt',
        sortOrder: 'desc',
      }),
    enabled: !hasUser,
  });

  const recItems = recs.data ?? [];
  const latestItems = latest.data?.items ?? [];
  const loading = hasUser ? recs.isLoading : latest.isLoading;
  const error = hasUser ? recs.isError : latest.isError;
  const empty = hasUser ? recItems.length === 0 : latestItems.length === 0;

  return (
    <MainLayout>
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="eyebrow mb-2">{hasUser ? '// for you' : '// feed'}</p>
          <h1 className="font-serif text-2xl font-bold text-ink">
            {hasUser ? '为你推荐' : '最新博文'}
          </h1>
        </div>
        {isAuthenticated && (
          <Link href="/editor">
            <Button theme="solid" icon={<IconPlus />} size="small">
              写博文
            </Button>
          </Link>
        )}
      </div>

      {loading ? (
        <PostList posts={[]} isLoading={true} hasMore={false} />
      ) : error && empty ? (
        <EmptyState title="暂时无法加载内容" description="后端服务暂不可用，请稍后再试" />
      ) : hasUser ? (
        recItems.length === 0 ? (
          <EmptyState title="暂无推荐" description="在设置里添加兴趣标签，或浏览更多文章后回来" />
        ) : (
          <div className="space-y-4">
            {recItems.map((item) => (
              <RecommendationCard key={item.id} item={item} />
            ))}
          </div>
        )
      ) : (
        <PostList posts={latestItems} isLoading={false} hasMore={false} />
      )}
    </MainLayout>
  );
}
