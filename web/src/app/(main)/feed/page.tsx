'use client';

import { useState, useEffect } from 'react';
import { Button, Toast } from '@douyinfe/semi-ui';
import { IconPlus } from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import { useAuthStore } from '@/store/authStore';
import { getFeed } from '@/lib/api/recommend';
import { getPosts } from '@/lib/api/posts';

const MOCK_POSTS = [
  {
    id: '1',
    title: 'Next.js 14 App Router 完全指南',
    content: '',
    summary: '深入探讨 Next.js 14 中 App Router 的核心概念、文件约定、路由机制以及最佳实践。',
    coverImage: '',
    authorId: 'mock-1',
    author: { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '' },
    tags: ['Next.js', 'React'],
    status: 'published' as const,
    likeCount: 128,
    commentCount: 32,
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
    tags: ['React', 'Server Components'],
    status: 'published' as const,
    likeCount: 256,
    commentCount: 67,
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
    tags: ['TypeScript', 'JavaScript'],
    status: 'published' as const,
    likeCount: 89,
    commentCount: 21,
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
    tags: ['TailwindCSS', 'CSS'],
    status: 'published' as const,
    likeCount: 312,
    commentCount: 45,
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
    likeCount: 178,
    commentCount: 53,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-21T09:00:00Z',
    updatedAt: '2026-03-21T09:00:00Z',
  },
];

export default function FeedPage() {
  const { user, isAuthenticated } = useAuthStore();
  const [allPosts, setAllPosts] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [useMock, setUseMock] = useState(false);

  useEffect(() => {
    if (!isAuthenticated || !user?.id) {
      // Not logged in — show mock data
      setUseMock(true);
      setAllPosts(MOCK_POSTS);
      setIsLoading(false);
      return;
    }

    let cancelled = false;
    setIsLoading(true);

    // Try personalized feed
    getFeed(user.id, { page: 1, size: 10 })
      .then((data) => {
        if (cancelled) return;
        if (data && data.length > 0) {
          const posts = data.map((fp: any) => ({
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
          setAllPosts(posts);
          setIsLoading(false);
        } else {
          // Fallback to latest posts
          return getPosts({
            page: 1,
            pageSize: 10,
            status: 'published',
            sortBy: 'createdAt',
            sortOrder: 'desc',
          });
        }
      })
      .then((data) => {
        if (cancelled || !data) return;
        if (data.items && data.items.length > 0) {
          setAllPosts(data.items);
        } else {
          setUseMock(true);
          setAllPosts(MOCK_POSTS);
        }
        setIsLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setUseMock(true);
        setAllPosts(MOCK_POSTS);
        setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, user?.id]);

  return (
    <MainLayout>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">推荐 Feed</h1>
          {!isAuthenticated && (
            <p className="text-gray-500 text-sm mt-1">登录后获得个性化推荐，当前显示热门内容</p>
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

      {useMock && (
        <p className="text-gray-400 text-sm mb-4">（演示数据 — 连接后端后显示真实内容）</p>
      )}
      <PostList posts={allPosts} isLoading={isLoading} hasMore={false} />
    </MainLayout>
  );
}
