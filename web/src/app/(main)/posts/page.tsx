'use client';

import { useState, useCallback, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { Tag, Button } from '@douyinfe/semi-ui';
import { IconSort } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import { useAuthStore } from '@/store/authStore';
import { usePosts } from '@/hooks/usePosts';
import { getPosts } from '@/lib/api/posts';
import Link from 'next/link';
import { IconPlus } from '@douyinfe/semi-icons';

type SortKey = 'createdAt' | 'likesCount' | 'commentsCount';

const MOCK_POSTS = [
  {
    id: '1', title: 'Next.js 14 App Router 完全指南', content: '',
    summary: '深入探讨 Next.js 14 中 App Router 的核心概念、文件约定、路由机制以及最佳实践。',
    coverImage: '', authorId: 'mock-1',
    author: { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '' },
    tags: ['Next.js', 'React'], status: 'published' as const,
    likesCount: 128, commentsCount: 32, isLiked: false, isFavorited: false,
    createdAt: '2026-03-25T10:30:00Z', updatedAt: '2026-03-25T10:30:00Z',
  },
  {
    id: '2', title: 'React Server Components 深度解析', content: '',
    summary: 'Server Components 是 React 的革命性特性，本文将带你从原理到实践全面掌握。',
    coverImage: '', authorId: 'mock-2',
    author: { id: 'mock-2', username: '前端达人', displayName: '前端达人', avatar: '' },
    tags: ['React', 'Server Components'], status: 'published' as const,
    likesCount: 256, commentsCount: 67, isLiked: false, isFavorited: false,
    createdAt: '2026-03-24T08:15:00Z', updatedAt: '2026-03-24T08:15:00Z',
  },
  {
    id: '3', title: 'TypeScript 5.0 新特性一览', content: '',
    summary: 'TypeScript 5.0 带来了装饰器、const 类型参数、inferred 类型等重磅新特性。',
    coverImage: '', authorId: 'mock-3',
    author: { id: 'mock-3', username: 'TS布道者', displayName: 'TS布道者', avatar: '' },
    tags: ['TypeScript', 'JavaScript'], status: 'published' as const,
    likesCount: 89, commentsCount: 21, isLiked: false, isFavorited: false,
    createdAt: '2026-03-23T14:20:00Z', updatedAt: '2026-03-23T14:20:00Z',
  },
  {
    id: '4', title: 'Tailwind CSS v4 实战技巧', content: '',
    summary: 'Tailwind CSS v4 引入了全新的引擎和诸多改进，让你的样式开发效率翻倍。',
    coverImage: '', authorId: 'mock-4',
    author: { id: 'mock-4', username: 'CSS魔法师', displayName: 'CSS魔法师', avatar: '' },
    tags: ['TailwindCSS', 'CSS'], status: 'published' as const,
    likesCount: 312, commentsCount: 45, isLiked: false, isFavorited: false,
    createdAt: '2026-03-22T16:40:00Z', updatedAt: '2026-03-22T16:40:00Z',
  },
  {
    id: '5', title: '构建高可用微服务架构实践', content: '',
    summary: '从服务拆分、API网关、服务发现到链路追踪，全面介绍微服务架构落地的最佳实践。',
    coverImage: '', authorId: 'mock-5',
    author: { id: 'mock-5', username: '架构师老李', displayName: '架构师老李', avatar: '' },
    tags: ['微服务', '架构', 'Go'], status: 'published' as const,
    likesCount: 178, commentsCount: 53, isLiked: false, isFavorited: false,
    createdAt: '2026-03-21T09:00:00Z', updatedAt: '2026-03-21T09:00:00Z',
  },
  {
    id: '6', title: 'Docker Compose 开发环境最佳配置', content: '',
    summary: '使用 Docker Compose 一键搭建包含数据库、缓存、消息队列的完整开发环境。',
    coverImage: '', authorId: 'mock-6',
    author: { id: 'mock-6', username: 'DevOps小王', displayName: 'DevOps小王', avatar: '' },
    tags: ['Docker', 'DevOps'], status: 'published' as const,
    likesCount: 95, commentsCount: 18, isLiked: false, isFavorited: false,
    createdAt: '2026-03-20T11:30:00Z', updatedAt: '2026-03-20T11:30:00Z',
  },
];

function PostsContent() {
  const searchParams = useSearchParams();
  const tag = searchParams.get('tag') || '';
  const authorId = searchParams.get('authorId') || '';
  const [sortBy, setSortBy] = useState<SortKey>('createdAt');
  const [page, setPage] = useState(1);
  const [allPosts, setAllPosts] = useState<any[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [useMock, setUseMock] = useState(false);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const { data } = usePosts({
    page,
    pageSize: 10,
    tag: tag || undefined,
    authorId: authorId || undefined,
    status: 'published',
    sortBy,
    sortOrder: 'desc',
  });

  // When data arrives from API
  useEffect(() => {
    if (data && data.items && data.items.length > 0) {
      setAllPosts((prev) => (page === 1 ? data.items : [...prev, ...data.items]));
      setHasMore(page < (data.totalPages || 1));
      setIsLoading(false);
    }
  }, [data, page]);

  // Fallback to mock when API returns nothing
  useEffect(() => {
    if (data && data.items && data.items.length === 0 && page === 1 && !tag && !authorId) {
      setUseMock(true);
      setAllPosts(MOCK_POSTS);
      setIsLoading(false);
    }
  }, [data, page, tag, authorId]);

  // Also try direct API call as fallback
  useEffect(() => {
    if (!data && isLoading) {
      getPosts({ page: 1, pageSize: 10, status: 'published', sortBy, sortOrder: 'desc' })
        .then((d) => {
          if (d && d.items && d.items.length > 0) {
            setAllPosts(d.items);
            setHasMore((d.totalPages || 1) > 1);
          } else if (!tag && !authorId) {
            setUseMock(true);
            setAllPosts(MOCK_POSTS);
          }
        })
        .catch(() => {
          if (!tag && !authorId) {
            setUseMock(true);
            setAllPosts(MOCK_POSTS);
          }
        })
        .finally(() => setIsLoading(false));
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSortChange = (key: SortKey) => {
    setSortBy(key);
    setPage(1);
    setAllPosts([]);
    setIsLoading(true);
    setUseMock(false);
  };

  const sortOptions = [
    { key: 'createdAt' as SortKey, label: '最新' },
    { key: 'likesCount' as SortKey, label: '最热' },
    { key: 'commentsCount' as SortKey, label: '最多评论' },
  ];

  return (
    <MainLayout>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {tag ? `#${tag}` : authorId ? '用户博文' : '所有博文'}
          </h1>
          {tag && (
            <Tag color="cyan" size="large" closable onClose={() => window.location.href = '/posts'}>
              {tag}
            </Tag>
          )}
        </div>
        {isAuthenticated && (
          <Link href="/editor">
            <Button theme="solid" icon={<IconPlus />} size="small">
              写博文
            </Button>
          </Link>
        )}
      </div>

      {/* 排序选项 */}
      <div className="flex items-center gap-2 mb-4">
        <IconSort style={{ color: '#94a3b8' }} />
        {sortOptions.map((option) => (
          <Button
            key={option.key}
            size="small"
            theme={sortBy === option.key ? 'solid' : 'borderless'}
            type={sortBy === option.key ? 'primary' : 'tertiary'}
            onClick={() => handleSortChange(option.key)}
          >
            {option.label}
          </Button>
        ))}
      </div>

      {useMock && <p className="text-gray-400 text-sm mb-4">（演示数据 — 连接后端后显示真实内容）</p>}
      <PostList
        posts={allPosts}
        isLoading={isLoading}
        hasMore={hasMore && !useMock}
        onLoadMore={() => setPage((p) => p + 1)}
      />
    </MainLayout>
  );
}

export default function PostsPage() {
  return (
    <Suspense fallback={
      <MainLayout>
        <div className="max-w-3xl mx-auto">
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-white rounded-lg border border-gray-200 p-5 animate-pulse">
                <div className="h-5 bg-gray-200 rounded w-3/4 mb-3" />
                <div className="h-4 bg-gray-100 rounded w-full mb-1" />
                <div className="h-4 bg-gray-100 rounded w-2/3" />
              </div>
            ))}
          </div>
        </div>
      </MainLayout>
    }>
      <PostsContent />
    </Suspense>
  );
}
