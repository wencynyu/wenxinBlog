'use client';

import { useState, useCallback, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@douyinfe/semi-ui';
import { IconSort } from '@douyinfe/semi-icons';
import Link from 'next/link';
import { IconPlus } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import { useAuthStore } from '@/store/authStore';
import { getPosts } from '@/lib/api/posts';
import type { Post, PostQueryParams } from '@/types/post';

type SortKey = 'createdAt' | 'likeCount' | 'commentCount';

function PostsContent() {
  const searchParams = useSearchParams();
  const tag = searchParams.get('tag') || undefined;
  const { isAuthenticated } = useAuthStore();
  const [allPosts, setAllPosts] = useState<Post[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [sortBy, setSortBy] = useState<SortKey>('createdAt');

  const sortOptions: { key: SortKey; label: string }[] = [
    { key: 'createdAt', label: '最新' },
    { key: 'likeCount', label: '最热' },
  ];

  const fetchPosts = useCallback(
    async (sort: SortKey) => {
      setIsLoading(true);
      try {
        const params: PostQueryParams = {
          page: 1,
          pageSize: 20,
          status: 'published',
          tag,
          sortBy: sort,
          sortOrder: 'desc',
        };
        const data = await getPosts(params);
        if (data?.items?.length) {
          setAllPosts(data.items);
          setLoadError(false);
        } else {
          setAllPosts([]);
        }
      } catch {
        setAllPosts([]);
        setLoadError(true);
      }
      setIsLoading(false);
    },
    [tag],
  );

  useEffect(() => {
    fetchPosts(sortBy);
  }, [fetchPosts, sortBy]);

  return (
    <MainLayout>
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="eyebrow mb-2">{'// posts' + (tag ? ` · #${tag}` : '')}</p>
          <h1 className="font-serif text-2xl font-bold text-ink">博文列表</h1>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1">
            {sortOptions.map((opt) => (
              <Button
                key={opt.key}
                size="small"
                theme={sortBy === opt.key ? 'solid' : 'borderless'}
                onClick={() => setSortBy(opt.key)}
              >
                {opt.label}
              </Button>
            ))}
          </div>
          {isAuthenticated && (
            <Link href="/editor">
              <Button theme="solid" icon={<IconPlus />} size="small">
                写博文
              </Button>
            </Link>
          )}
        </div>
      </div>

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
