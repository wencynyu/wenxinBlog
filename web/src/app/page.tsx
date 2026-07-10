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
              likesCount: fp.likesCount,
              commentsCount: fp.commentsCount,
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
      {/* Hero Banner */}
      <div className="bg-gradient-to-br from-sky-500 to-blue-600 rounded-2xl p-8 mb-8 text-white">
        <h1 className="text-3xl font-bold mb-2">欢迎来到 WenxinBlog</h1>
        <p className="text-sky-100 text-lg mb-6">
          {isAuthenticated
            ? '发现优质内容，分享你的技术见解'
            : '基于 Next.js 14 和 Semi-Design 的现代化技术博文平台'}
        </p>
        {isAuthenticated ? (
          <Link href="/editor">
            <Button
              theme="solid"
              icon={<IconPlus />}
              size="large"
              className="!bg-white !text-sky-600 hover:!bg-sky-50"
            >
              写博文
            </Button>
          </Link>
        ) : (
          <div className="flex gap-3">
            <Link href="/register">
              <Button
                theme="solid"
                size="large"
                className="!bg-white !text-sky-600 hover:!bg-sky-50"
              >
                免费注册
              </Button>
            </Link>
            <Link href="/login">
              <Button
                size="large"
                className="!border-2 !border-white/50 !text-white hover:!bg-white/10"
              >
                登录
              </Button>
            </Link>
          </div>
        )}
      </div>

      {/* Post list */}
      {loadError && allPosts.length === 0 ? (
        <EmptyState title="暂时无法加载内容" description="后端服务暂不可用，请稍后再试" />
      ) : (
        <PostList posts={allPosts} isLoading={isLoading} hasMore={hasMore} onLoadMore={loadMore} />
      )}
    </MainLayout>
  );
}
