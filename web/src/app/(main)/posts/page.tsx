'use client';

import { useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button, Radio } from '@douyinfe/semi-ui';
import Link from 'next/link';
import { IconPlus } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import PageHeader from '@/components/common/PageHeader';
import { useAuthStore } from '@/store/authStore';
import { usePosts } from '@/hooks/usePosts';

type SortKey = 'createdAt' | 'likeCount' | 'commentCount';

function PostsContent() {
  const searchParams = useSearchParams();
  const tag = searchParams.get('tag') || undefined;
  const { isAuthenticated } = useAuthStore();
  const [sortBy, setSortBy] = useState<SortKey>('createdAt');

  const sortOptions: { key: SortKey; label: string }[] = [
    { key: 'createdAt', label: '最新' },
    { key: 'likeCount', label: '最热' },
  ];

  // 走 react-query：sortBy/tag 变化时 queryKey 随之变化自动重取，严格模式下也不再双发。
  const { data, isLoading, isError } = usePosts({
    page: 1,
    pageSize: 20,
    status: 'published',
    tag,
    sortBy,
    sortOrder: 'desc',
  });
  const allPosts = data?.items ?? [];
  const loadError = isError;

  return (
    <MainLayout>
      <PageHeader
        eyebrow={'// posts' + (tag ? ` · #${tag}` : '')}
        title="博文列表"
        extra={
          <>
            <Radio.Group
              type="button"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortKey)}
            >
              {sortOptions.map((opt) => (
                <Radio key={opt.key} value={opt.key}>
                  {opt.label}
                </Radio>
              ))}
            </Radio.Group>
            {isAuthenticated && (
              <Link href="/editor">
                <Button theme="solid" icon={<IconPlus />} size="small">
                  写博文
                </Button>
              </Link>
            )}
          </>
        }
      />

      {loadError && allPosts.length === 0 ? (
        <EmptyState title="暂时无法加载内容" description="后端服务暂不可用，请稍后再试" />
      ) : (
        <PostList posts={allPosts} isLoading={isLoading} hasMore={false} />
      )}
    </MainLayout>
  );
}

export default function PostsPage() {
  return (
    <Suspense fallback={null}>
      <PostsContent />
    </Suspense>
  );
}
