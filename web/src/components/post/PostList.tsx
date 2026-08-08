'use client';

import { useEffect, useRef, useCallback } from 'react';
import { Skeleton, Empty, Button } from '@douyinfe/semi-ui';
import PostCard from './PostCard';
import type { Post } from '@/types/post';

interface PostListProps {
  posts: Post[];
  isLoading: boolean;
  isFetching?: boolean;
  hasMore?: boolean;
  onLoadMore?: () => void;
  error?: Error | null;
}

function PostCardSkeleton() {
  return (
    <div className="bg-surface rounded-xl shadow-card p-5">
      <div className="flex items-center mb-3">
        <Skeleton.Avatar size="small" />
        <div className="ml-2 flex-1">
          <Skeleton.Title style={{ width: 80, height: 14 }} />
        </div>
      </div>
      <Skeleton.Title style={{ width: '70%', marginBottom: 12 }} />
      <Skeleton.Paragraph style={{ width: '100%' }} />
      <div className="flex gap-2 mt-3">
        <Skeleton.Title style={{ width: 50, height: 22 }} />
        <Skeleton.Title style={{ width: 50, height: 22 }} />
      </div>
    </div>
  );
}

export default function PostList({
  posts,
  isLoading,
  isFetching,
  hasMore = false,
  onLoadMore,
  error,
}: PostListProps) {
  const observerRef = useRef<IntersectionObserver | null>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);

  const handleObserver = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const [entry] = entries;
      if (entry.isIntersecting && hasMore && !isLoading && !isFetching) {
        onLoadMore?.();
      }
    },
    [hasMore, isLoading, isFetching, onLoadMore],
  );

  useEffect(() => {
    if (observerRef.current) {
      observerRef.current.disconnect();
    }

    observerRef.current = new IntersectionObserver(handleObserver, {
      rootMargin: '200px',
    });

    const currentRef = loadMoreRef.current;
    if (currentRef) {
      observerRef.current.observe(currentRef);
    }

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, [handleObserver]);

  if (isLoading) {
    return (
      <div className="space-y-6">
        {Array.from({ length: 3 }).map((_, i) => (
          <PostCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-surface rounded-xl shadow-card p-8 text-center">
        <Empty title="加载失败" description={error.message || '请稍后重试'} />
      </div>
    );
  }

  if (!posts || posts.length === 0) {
    return (
      <div className="bg-surface rounded-xl shadow-card p-8 text-center">
        <Empty title="暂无博文" description="还没有发布任何博文" />
      </div>
    );
  }

  return (
    <div>
      <div className="space-y-6">
        {posts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </div>

      {/* 无限滚动触发器 */}
      {hasMore && <div ref={loadMoreRef} className="h-1" />}

      {/* 加载更多指示器 */}
      {isFetching && (
        <div className="py-4">
          <PostCardSkeleton />
        </div>
      )}

      {/* 手动加载更多 */}
      {hasMore && !isFetching && (
        <div className="text-center py-4">
          <Button theme="borderless" onClick={onLoadMore} disabled={isFetching}>
            加载更多
          </Button>
        </div>
      )}
    </div>
  );
}
