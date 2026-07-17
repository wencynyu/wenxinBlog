'use client';

import { Button } from '@douyinfe/semi-ui';
import { IconPlus } from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import { useAuthStore } from '@/store/authStore';
import { usePosts } from '@/hooks/usePosts';

export default function FeedPage() {
  const { isAuthenticated } = useAuthStore();
  // 走 react-query（usePosts）：自动去重（严格模式下不再发两次）、5 分钟缓存、错误重试。
  const { data, isLoading, isError } = usePosts({
    page: 1,
    pageSize: 10,
    status: 'published',
    sortBy: 'createdAt',
    sortOrder: 'desc',
  });
  const allPosts = data?.items ?? [];
  const loadError = isError;

  return (
    <MainLayout>
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="eyebrow mb-2">{'// feed'}</p>
          <h1 className="font-serif text-2xl font-bold text-ink">最新博文</h1>
        </div>
        {isAuthenticated && (
          <Link href="/editor">
            <Button theme="solid" icon={<IconPlus />} size="small">
              写博文
            </Button>
          </Link>
        )}
      </div>

      {loadError && allPosts.length === 0 ? (
        <EmptyState title="暂时无法加载内容" description="后端服务暂不可用，请稍后再试" />
      ) : (
        <PostList posts={allPosts} isLoading={isLoading} hasMore={false} />
      )}
    </MainLayout>
  );
}
