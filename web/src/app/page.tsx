'use client';

import Link from 'next/link';
import { Button } from '@douyinfe/semi-ui';
import { IconPlus } from '@douyinfe/semi-icons';
import { useInfiniteQuery } from '@tanstack/react-query';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import { useAuthStore } from '@/store/authStore';
import { getPosts } from '@/lib/api/posts';

export default function HomePage() {
  const { user, isAuthenticated } = useAuthStore();

  // 无限分页（loadMore）走 react-query：自动去重（严格模式下不再发两次）、缓存、错误重试。
  const { data, isLoading, isError, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useInfiniteQuery({
      queryKey: ['posts', 'home-feed'],
      queryFn: ({ pageParam }) =>
        getPosts({
          page: pageParam,
          pageSize: 10,
          status: 'published',
          sortBy: 'createdAt',
          sortOrder: 'desc',
        }),
      initialPageParam: 1,
      getNextPageParam: (lastPage) =>
        lastPage.page < lastPage.totalPages ? lastPage.page + 1 : undefined,
    });

  const allPosts = data?.pages.flatMap((p) => p.items) ?? [];
  const hasMore = hasNextPage;
  const loadError = isError;
  const loadMore = () => fetchNextPage();

  return (
    <MainLayout>
      {/* Hero */}
      <section className="relative mb-6 overflow-hidden rounded-2xl border border-hairline bg-surface p-6 sm:p-8">
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background: 'radial-gradient(70% 130% at 0% 0%, rgba(0,119,250,0.12), transparent 55%)',
          }}
          aria-hidden
        />
        <div className="relative">
          <p className="eyebrow mb-3">{'// wenxinblog · engineering folio'}</p>
          <h1 className="text-2xl sm:text-3xl font-semibold text-ink tracking-tight mb-2 text-balance">
            {isAuthenticated ? '发现值得读的技术文章' : '为工程师而生的技术博文平台'}
          </h1>
          <p className="text-ink-muted text-sm sm:text-base max-w-2xl mb-5">
            {isAuthenticated
              ? '基于你的兴趣与阅读行为，精选高质量工程实践内容。'
              : '基于 Next.js 14 与微服务架构，记录、分享与发现工程实践。'}
          </p>
          {isAuthenticated ? (
            <Link href="/editor">
              <Button theme="solid" icon={<IconPlus />} size="large">
                写博文
              </Button>
            </Link>
          ) : (
            <div className="flex gap-3">
              <Link href="/register">
                <Button theme="solid" size="large">
                  免费注册
                </Button>
              </Link>
              <Link href="/login">
                <Button size="large" theme="borderless">
                  登录
                </Button>
              </Link>
            </div>
          )}
        </div>
      </section>

      {/* Post list */}
      <p className="eyebrow mb-4">{'// latest posts'}</p>
      {loadError && allPosts.length === 0 ? (
        <EmptyState title="暂时无法加载内容" description="后端服务暂不可用，请稍后再试" />
      ) : (
        <PostList
          posts={allPosts}
          isLoading={isLoading}
          isFetching={isFetchingNextPage}
          hasMore={hasMore}
          onLoadMore={loadMore}
        />
      )}
    </MainLayout>
  );
}
