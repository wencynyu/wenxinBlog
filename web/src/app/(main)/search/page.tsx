'use client';

import { useState, useEffect, useRef, useCallback, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { Input, Tabs, TabPane, Tag, Button, Avatar, Typography } from '@douyinfe/semi-ui';
import { IconSearch, IconClock } from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';

const { Text } = Typography;

const MOCK_TRENDING = [
  'React', 'Next.js', 'TypeScript', 'Go', 'Docker',
  '微服务', 'TailwindCSS', 'PostgreSQL', 'Redis', 'Kubernetes',
];

const MOCK_SEARCH_POSTS = [
  {
    id: '1', title: 'Next.js 14 App Router 完全指南',
    summary: '深入探讨 Next.js 14 中 App Router 的核心概念、文件约定、路由机制以及最佳实践。',
    author: { displayName: '技术小王', username: 'tech-wang' },
    tags: ['Next.js', 'React'], likesCount: 128, commentsCount: 32,
  },
  {
    id: '2', title: 'React Server Components 深度解析',
    summary: 'Server Components 是 React 的革命性特性，本文将带你从原理到实践全面掌握。',
    author: { displayName: '前端达人', username: 'frontend-master' },
    tags: ['React'], likesCount: 256, commentsCount: 67,
  },
  {
    id: '3', title: 'TypeScript 5.0 新特性一览',
    summary: 'TypeScript 5.0 带来了装饰器、const 类型参数、inferred 类型等重磅新特性。',
    author: { displayName: 'TS布道者', username: 'ts-preacher' },
    tags: ['TypeScript', 'JavaScript'], likesCount: 89, commentsCount: 21,
  },
];

const MOCK_SEARCH_USERS = [
  { id: 'mock-1', username: 'tech-wang', displayName: '技术小王', avatar: '', bio: '全栈开发工程师', followersCount: 1200, postsCount: 45 },
  { id: 'mock-2', username: 'frontend-master', displayName: '前端达人', avatar: '', bio: 'React 爱好者', followersCount: 890, postsCount: 32 },
  { id: 'mock-3', username: 'go-expert', displayName: 'Go专家', avatar: '', bio: '后端架构师', followersCount: 2100, postsCount: 67 },
];

function SearchContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialQuery = searchParams.get('q') || '';

  const [query, setQuery] = useState(initialQuery);
  const [activeTab, setActiveTab] = useState('posts');
  const [hasSearched, setHasSearched] = useState(!!initialQuery);
  const inputRef = useRef<any>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleSearch = useCallback((value: string) => {
    const trimmed = value.trim();
    setQuery(trimmed);
    if (trimmed) {
      setHasSearched(true);
      router.replace(`/search?q=${encodeURIComponent(trimmed)}`);
    } else {
      setHasSearched(false);
    }
  }, [router]);

  const showHistoryOrTrending = !hasSearched;

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-3xl mx-auto">
        {/* 搜索框 */}
        <div className="mb-6">
          <Input
            ref={inputRef}
            value={query}
            onChange={setQuery}
            onEnterPress={(e: any) => handleSearch(e.target.value)}
            prefix={<IconSearch />}
            placeholder="搜索博文、用户..."
            size="large"
          />
        </div>

        {/* 搜索结果 */}
        {hasSearched && (
          <Tabs activeKey={activeTab} onChange={setActiveTab} type="line">
            <TabPane tab="博文" itemKey="posts">
              <div className="space-y-4 py-4">
                {MOCK_SEARCH_POSTS.map((post) => (
                  <Link
                    key={post.id}
                    href={`/posts/${post.id}`}
                    className="block p-4 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors border border-gray-100 dark:border-gray-700"
                  >
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-1 line-clamp-1 hover:text-sky-500">
                      {post.title}
                    </h3>
                    {post.summary && (
                      <p className="text-gray-500 text-sm line-clamp-2 mb-2">{post.summary}</p>
                    )}
                    <div className="flex items-center gap-3 text-gray-400 text-xs">
                      <span>{post.author?.displayName || post.author?.username}</span>
                      {post.tags?.map((tag) => (
                        <Tag key={tag} size="small" color="cyan">#{tag}</Tag>
                      ))}
                      <span>{post.likesCount} 赞</span>
                      <span>{post.commentsCount} 评论</span>
                    </div>
                  </Link>
                ))}
                <p className="text-gray-400 text-sm text-center py-4">（演示数据 — 连接后端后显示真实搜索结果）</p>
              </div>
            </TabPane>

            <TabPane tab="用户" itemKey="users">
              <div className="space-y-3 py-4">
                {MOCK_SEARCH_USERS.map((user) => (
                  <Link
                    key={user.id}
                    href={`/user/${user.id}`}
                    className="flex items-center gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <Avatar size="default" src={user.avatar} alt={user.username}>
                      {(user.displayName || user.username || 'U')[0]}
                    </Avatar>
                    <div className="flex-1">
                      <Text strong className="dark:text-gray-100">{user.displayName || user.username}</Text>
                      <br />
                      <Text type="tertiary" size="small">@{user.username}</Text>
                      {user.bio && (
                        <p className="text-gray-500 text-sm mt-1 line-clamp-1">{user.bio}</p>
                      )}
                    </div>
                    <div className="text-right text-gray-400 text-xs">
                      <div>{user.followersCount} 粉丝</div>
                      <div>{user.postsCount} 博文</div>
                    </div>
                  </Link>
                ))}
                <p className="text-gray-400 text-sm text-center py-4">（演示数据 — 连接后端后显示真实搜索结果）</p>
              </div>
            </TabPane>
          </Tabs>
        )}

        {/* 热门搜索 */}
        {showHistoryOrTrending && (
          <div className="py-4">
            <div>
              <h3 className="text-sm font-semibold text-gray-500 mb-3 flex items-center gap-1">
                <IconClock />
                热门搜索
              </h3>
              <div className="space-y-2">
                {MOCK_TRENDING.map((item, index) => (
                  <button
                    key={item}
                    onClick={() => handleSearch(item)}
                    className="flex items-center w-full p-2 rounded hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors text-left"
                  >
                    <span className={`w-6 text-center font-bold mr-3 ${
                      index < 3 ? 'text-red-500' : 'text-gray-400'
                    }`}>
                      {index + 1}
                    </span>
                    <span className="text-gray-700 dark:text-gray-300">{item}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto">
          <div className="h-12 bg-gray-100 rounded-lg mb-6 animate-pulse" />
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="animate-pulse">
                <div className="h-5 bg-gray-200 rounded w-3/4 mb-2" />
                <div className="h-4 bg-gray-100 rounded w-full mb-1" />
                <div className="h-4 bg-gray-100 rounded w-2/3" />
              </div>
            ))}
          </div>
        </div>
      </MainLayout>
    }>
      <SearchContent />
    </Suspense>
  );
}
