'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import { Button, Toast, Empty } from '@douyinfe/semi-ui';
import { IconPlus, IconStarStroked } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import { useAuthStore } from '@/store/authStore';
import { getFeed, getTrendingPosts } from '@/lib/api/recommend';
import { getPosts } from '@/lib/api/posts';

// Mock data for when backend is unavailable
const MOCK_POSTS = [
  {
    id: '1',
    title: 'Next.js 14 App Router 完全指南',
    content: '',
    summary: '深入探讨 Next.js 14 中 App Router 的核心概念、文件约定、路由机制以及最佳实践。',
    coverImage: '',
    authorId: 'mock-1',
    author: { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '' },
    tags: ['Next.js', 'React', '前端'],
    status: 'published' as const,
    likesCount: 128,
    commentsCount: 32,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-25T10:30:00Z',
    updatedAt: '2026-03-25T10:30:00Z',
  },
  {
    id: '2',
    title: 'React Server Components 深度解析',
    content: '',
    summary: 'Server Components 是 React 的革命性特性，本文将带你从原理到实践全面掌握。',
    coverImage: '',
    authorId: 'mock-2',
    author: { id: 'mock-2', username: '前端达人', displayName: '前端达人', avatar: '' },
    tags: ['React', 'Server Components', '性能优化'],
    status: 'published' as const,
    likesCount: 256,
    commentsCount: 67,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-24T08:15:00Z',
    updatedAt: '2026-03-24T08:15:00Z',
  },
  {
    id: '3',
    title: 'TypeScript 5.0 新特性一览',
    content: '',
    summary: 'TypeScript 5.0 带来了装饰器、const 类型参数、inferred 类型等重磅新特性。',
    coverImage: '',
    authorId: 'mock-3',
    author: { id: 'mock-3', username: 'TS布道者', displayName: 'TS布道者', avatar: '' },
    tags: ['TypeScript', 'JavaScript', '编程语言'],
    status: 'published' as const,
    likesCount: 89,
    commentsCount: 21,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-23T14:20:00Z',
    updatedAt: '2026-03-23T14:20:00Z',
  },
  {
    id: '4',
    title: 'Tailwind CSS v4 实战技巧',
    content: '',
    summary: 'Tailwind CSS v4 引入了全新的引擎和诸多改进，让你的样式开发效率翻倍。',
    coverImage: '',
    authorId: 'mock-4',
    author: { id: 'mock-4', username: 'CSS魔法师', displayName: 'CSS魔法师', avatar: '' },
    tags: ['TailwindCSS', 'CSS', 'UI'],
    status: 'published' as const,
    likesCount: 312,
    commentsCount: 45,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-22T16:40:00Z',
    updatedAt: '2026-03-22T16:40:00Z',
  },
  {
    id: '5',
    title: '构建高可用微服务架构实践',
    content: '',
    summary: '从服务拆分、API网关、服务发现到链路追踪，全面介绍微服务架构落地的最佳实践。',
    coverImage: '',
    authorId: 'mock-5',
    author: { id: 'mock-5', username: '架构师老李', displayName: '架构师老李', avatar: '' },
    tags: ['微服务', '架构', 'Go'],
    status: 'published' as const,
    likesCount: 178,
    commentsCount: 53,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-21T09:00:00Z',
    updatedAt: '2026-03-21T09:00:00Z',
  },
  {
    id: '6',
    title: 'Docker Compose 开发环境最佳配置',
    content: '',
    summary: '使用 Docker Compose 一键搭建包含数据库、缓存、消息队列的完整开发环境。',
    coverImage: '',
    authorId: 'mock-6',
    author: { id: 'mock-6', username: 'DevOps小王', displayName: 'DevOps小王', avatar: '' },
    tags: ['Docker', 'DevOps', '开发工具'],
    status: 'published' as const,
    likesCount: 95,
    commentsCount: 18,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-20T11:30:00Z',
    updatedAt: '2026-03-20T11:30:00Z',
  },
];

const TRENDING_MOCK = [
  { id: '7', title: '2026年前端技术趋势预测', likesCount: 456 },
  { id: '8', title: '从零到一搭建 CI/CD 流水线', likesCount: 342 },
  { id: '9', title: 'React vs Vue vs Svelte 对比评测', likesCount: 289 },
];

function PostCardSkeleton() {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">
      <div className="flex items-center mb-3">
        <div className="w-8 h-8 rounded-full bg-gray-200 animate-pulse" />
        <div className="ml-3 flex-1">
          <div className="h-3.5 w-20 bg-gray-200 rounded animate-pulse" />
        </div>
      </div>
      <div className="h-5 w-3/4 bg-gray-200 rounded animate-pulse mb-3" />
      <div className="space-y-2">
        <div className="h-3 w-full bg-gray-100 rounded animate-pulse" />
        <div className="h-3 w-4/5 bg-gray-100 rounded animate-pulse" />
      </div>
      <div className="flex gap-2 mt-3">
        <div className="h-5 w-14 bg-gray-100 rounded-full animate-pulse" />
        <div className="h-5 w-14 bg-gray-100 rounded-full animate-pulse" />
      </div>
    </div>
  );
}

export default function HomePage() {
  const { user, isAuthenticated, isLoading: authLoading } = useAuthStore();
  const [allPosts, setAllPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [useMock, setUseMock] = useState(false);
  const [page, setPage] = useState(1);
  const dataLoadedRef = useRef(false);

  // Try loading from API
  const fetchPosts = useCallback(async (p: number) => {
    if (isAuthenticated && user?.id) {
      try {
        const feedData = await getFeed(user.id, { page: p, size: 10 });
        if (feedData && feedData.length > 0) {
          const posts = feedData.map((fp: any) => ({
            id: fp.id, title: fp.title, content: '',
            summary: fp.summary || '', coverImage: fp.coverImage,
            authorId: fp.authorId, author: fp.author, tags: fp.tags || [],
            status: 'published', likesCount: fp.likesCount, commentsCount: fp.commentsCount,
            isLiked: false, isFavorited: false,
            createdAt: fp.createdAt, updatedAt: fp.createdAt,
          }));
          setAllPosts((prev) => (p === 1 ? posts : [...prev, ...posts]));
          setHasMore(feedData.length >= 10);
          dataLoadedRef.current = true;
          setIsLoading(false);
          return;
        }
      } catch {}
    }
    try {
      const data = await getPosts({ page: p, pageSize: 10, status: 'published', sortBy: 'createdAt', sortOrder: 'desc' });
      if (data && data.items && data.items.length > 0) {
        setAllPosts((prev) => (p === 1 ? data.items : [...prev, ...data.items]));
        setHasMore(p < (data.totalPages || 1));
        dataLoadedRef.current = true;
        setIsLoading(false);
        return;
      }
    } catch {}
    // Backend unavailable - use mock data
    setUseMock(true);
    setIsLoading(false);
    dataLoadedRef.current = true;
    setAllPosts(MOCK_POSTS);
  }, [isAuthenticated, user?.id]);

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
            <Button theme="solid" icon={<IconPlus />} size="large" className="!bg-white !text-sky-600 hover:!bg-sky-50">
              写博文
            </Button>
          </Link>
        ) : (
          <div className="flex gap-3">
            <Link href="/register">
              <Button theme="solid" size="large" className="!bg-white !text-sky-600 hover:!bg-sky-50">
                免费注册
              </Button>
            </Link>
            <Link href="/login">
              <Button size="large" className="!border-2 !border-white/50 !text-white hover:!bg-white/10">
                登录
              </Button>
            </Link>
          </div>
        )}
      </div>

      {/* Trending sidebar content */}
      {useMock && TRENDING_MOCK.length > 0 && (
        <div className="mb-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-3 flex items-center gap-2">
            <IconStarStroked className="text-amber-500" /> 热门推荐
          </h2>
          <div className="flex flex-wrap gap-2">
            {TRENDING_MOCK.map((item) => (
              <Link
                key={item.id}
                href={`/posts/${item.id}`}
                className="bg-white rounded-lg border border-gray-200 px-4 py-2 text-sm text-gray-700 hover:border-sky-300 hover:text-sky-500 transition-all"
              >
                {item.title}
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* Post list */}
      {useMock && <p className="text-gray-400 text-sm mb-4">（演示数据 — 连接后端后显示真实内容）</p>}
      <PostList
        posts={allPosts}
        isLoading={isLoading}
        hasMore={hasMore && !useMock}
        onLoadMore={loadMore}
      />
    </MainLayout>
  );
}
