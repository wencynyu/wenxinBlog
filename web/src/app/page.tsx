'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import { Button } from '@douyinfe/semi-ui';
import { IconPlus } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import { useAuthStore } from '@/store/authStore';
import { getFeed } from '@/lib/api/recommend';
import { getPosts } from '@/lib/api/posts';

export default function HomePage() {
  const { user, isAuthenticated } = useAuthStore();
  const [allPosts, setAllPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [page, setPage] = useState(1);
  const dataLoadedRef = useRef(false);

  // Load from API. On failure the UI shows an EmptyState instead of mock data.
  const fetchPosts = useCallback(
    async (p: number) => {
      if (isAuthenticated && user?.id) {
        try {
          const feedData = await getFeed(user.id, { page: p, size: 10 });
          if (feedData && feedData.length > 0) {
            const posts = feedData.map((fp: any) => ({
              id: fp.id,
              title: fp.title,
              content: '',
              summary: fp.summary || '',
              coverImage: fp.coverImage,
              authorId: fp.authorId,
              author: fp.author,
              tags: fp.tags || [],
              status: 'published',
              likeCount: fp.likeCount,
              commentCount: fp.commentCount,
              isLiked: false,
              isFavorited: false,
              createdAt: fp.createdAt,
              updatedAt: fp.createdAt,
            }));
            setAllPosts((prev) => (p === 1 ? posts : [...prev, ...posts]));
            setHasMore(feedData.length >= 10);
            setLoadError(false);
            dataLoadedRef.current = true;
            setIsLoading(false);
            return;
          }
        } catch {}
      }
      try {
        const data = await getPosts({
          page: p,
          pageSize: 10,
          status: 'published',
          sortBy: 'createdAt',
          sortOrder: 'desc',
        });
        if (data && data.items && data.items.length > 0) {
          setAllPosts((prev) => (p === 1 ? data.items : [...prev, ...data.items]));
          setHasMore(p < (data.totalPages || 1));
          setLoadError(false);
          dataLoadedRef.current = true;
          setIsLoading(false);
          return;
        }
      } catch {}
      // Backend unavailable — surface an error state instead of mock data.
      if (p === 1) {
        setAllPosts([]);
        setLoadError(true);
      }
      setHasMore(false);
      setIsLoading(false);
      dataLoadedRef.current = true;
    },
    [isAuthenticated, user?.id],
  );

  useEffect(() => {
    fetchPosts(page);
  }, [fetchPosts, page]);

  const loadMore = useCallback(() => setPage((p) => p + 1), []);

  return (
    <MainLayout>
      {/* Hero */}
      <section className="mb-10">
        <p className="eyebrow mb-3">{'// wenxinblog · engineering folio'}</p>
        <h1 className="font-serif text-4xl sm:text-5xl font-semibold text-ink tracking-tight mb-3 text-balance">
          {isAuthenticated ? '发现值得读的技术文章' : '为工程师而生的技术博文平台'}
        </h1>
        <p className="text-ink-muted text-lg max-w-2xl mb-6">
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
              <Button size="large" theme="borderless" className="!text-primary-700 !font-medium">
                登录
              </Button>
            </Link>
          </div>
        )}
      </section>

      {/* Post list */}
      <p className="eyebrow mb-4">{'// latest posts'}</p>
      {loadError && allPosts.length === 0 ? (
        <EmptyState title="暂时无法加载内容" description="后端服务暂不可用，请稍后再试" />
      ) : (
        <PostList posts={allPosts} isLoading={isLoading} hasMore={hasMore} onLoadMore={loadMore} />
      )}
    </MainLayout>
  );
}
