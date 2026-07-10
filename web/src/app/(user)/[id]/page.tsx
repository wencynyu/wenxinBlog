'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { Tabs, TabPane, Avatar, Button, Toast } from '@douyinfe/semi-ui';
import { IconUserAdd, IconDelete } from '@douyinfe/semi-icons';
import Link from 'next/link';
import { useAuthStore } from '@/store/authStore';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import { getPosts } from '@/lib/api/posts';
import { getUserProfile } from '@/lib/api/users';

const MOCK_PROFILES: Record<string, any> = {
  'mock-1': { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '', bio: '全栈开发工程师，热爱技术分享', location: '北京', website: 'https://example.com', postsCount: 45, followersCount: 1200, followingCount: 89, isFollowing: false },
  'mock-2': { id: 'mock-2', username: '前端达人', displayName: '前端达人', avatar: '', bio: 'React/Vue/Next.js 爱好者', postsCount: 32, followersCount: 890, followingCount: 156, isFollowing: false },
};

const DEFAULT_PROFILE = { id: 'unknown', username: 'WenxinBlog 用户', displayName: 'WenxinBlog 用户', avatar: '', bio: '这是一个演示用户', postsCount: 10, followersCount: 100, followingCount: 50, isFollowing: false };

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
    coverImage: '', authorId: 'mock-1',
    author: { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '' },
    tags: ['React', 'Server Components'], status: 'published' as const,
    likesCount: 256, commentsCount: 67, isLiked: false, isFavorited: false,
    createdAt: '2026-03-24T08:15:00Z', updatedAt: '2026-03-24T08:15:00Z',
  },
];

export default function UserProfilePage() {
  const params = useParams();
  const userId = params.id as string;
  const currentUser = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [profile, setProfile] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [useMock, setUseMock] = useState(false);
  const [activeTab, setActiveTab] = useState('posts');
  const [posts, setPosts] = useState<any[]>([]);
  const [postsLoading, setPostsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);

    getUserProfile(userId)
      .then((data) => {
        if (!cancelled && data) {
          setProfile(data);
        } else if (!cancelled) {
          setProfile(MOCK_PROFILES[userId] || { ...DEFAULT_PROFILE, id: userId });
          setUseMock(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setProfile(MOCK_PROFILES[userId] || { ...DEFAULT_PROFILE, id: userId });
          setUseMock(true);
        }
      })
      .finally(() => { if (!cancelled) setIsLoading(false); });

    getPosts({ page: 1, pageSize: 10, authorId: userId, status: 'published', sortBy: 'createdAt', sortOrder: 'desc' })
      .then((data) => {
        if (!cancelled && data && data.items && data.items.length > 0) {
          setPosts(data.items);
        } else if (!cancelled) {
          setPosts(MOCK_POSTS);
        }
      })
      .catch(() => {
        if (!cancelled) setPosts(MOCK_POSTS);
      })
      .finally(() => { if (!cancelled) setPostsLoading(false); });

    return () => { cancelled = true; };
  }, [userId]);

  const isSelf = currentUser && currentUser.id === userId;
  const isFollowing = profile?.isFollowing;

  const handleToggleFollow = () => {
    if (!isAuthenticated) {
      Toast.warning('请先登录');
      return;
    }
    // Mock toggle
    setProfile((prev: any) => ({ ...prev, isFollowing: !prev.isFollowing }));
    Toast.success(isFollowing ? '已取消关注' : '关注成功');
  };

  if (isLoading) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto">
          <div className="bg-white rounded-lg border border-gray-200 p-8 animate-pulse dark:bg-gray-800 dark:border-gray-700">
            <div className="flex items-center gap-6">
              <div className="w-20 h-20 bg-gray-200 rounded-full" />
              <div className="flex-1">
                <div className="h-6 bg-gray-200 rounded w-40 mb-3" />
                <div className="h-4 bg-gray-100 rounded w-24" />
              </div>
            </div>
          </div>
        </div>
      </MainLayout>
    );
  }

  if (!profile) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto text-center py-20">
          <h3 className="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">用户不存在</h3>
          <Link href="/" className="text-sky-500 hover:text-sky-600">返回首页</Link>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-3xl mx-auto">
        {/* 用户信息卡片 */}
        <div className="bg-white rounded-lg border border-gray-200 p-8 mb-6 dark:bg-gray-800 dark:border-gray-700">
          {useMock && <p className="text-gray-400 text-sm mb-3">（演示数据 — 连接后端后显示真实内容）</p>}
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-6">
              <Avatar
                size="extra-large"
                src={profile.avatar}
                alt={profile.displayName || profile.username}
              >
                {(profile.displayName || profile.username || 'U')[0]}
              </Avatar>
              <div>
                <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">
                  {profile.displayName || profile.username}
                </h3>
                <span className="text-gray-400">@{profile.username}</span>
                {profile.bio && (
                  <p className="text-gray-600 dark:text-gray-400 mt-2">{profile.bio}</p>
                )}
                {profile.location && (
                  <p className="text-gray-400 text-sm mt-1">{profile.location}</p>
                )}
              </div>
            </div>

            {!isSelf && (
              <Button
                theme={isFollowing ? 'borderless' : 'solid'}
                icon={isFollowing ? <IconDelete /> : <IconUserAdd />}
                onClick={handleToggleFollow}
              >
                {isFollowing ? '已关注' : '关注'}
              </Button>
            )}
          </div>

          {/* 统计 */}
          <div className="flex gap-8 mt-6 pt-6 border-t border-gray-100 dark:border-gray-700">
            <div className="text-center">
              <span className="text-xl font-bold text-gray-900 dark:text-gray-100 block">{profile.postsCount || 0}</span>
              <span className="text-gray-400 text-sm">博文</span>
            </div>
            <div className="text-center">
              <span className="text-xl font-bold text-gray-900 dark:text-gray-100 block">{profile.followersCount || 0}</span>
              <span className="text-gray-400 text-sm">粉丝</span>
            </div>
            <div className="text-center">
              <span className="text-xl font-bold text-gray-900 dark:text-gray-100 block">{profile.followingCount || 0}</span>
              <span className="text-gray-400 text-sm">关注</span>
            </div>
          </div>
        </div>

        {/* Tab 切换 */}
        <div className="bg-white rounded-lg border border-gray-200 dark:bg-gray-800 dark:border-gray-700">
          <Tabs activeKey={activeTab} onChange={setActiveTab} type="line">
            <TabPane tab="博文" itemKey="posts">
              <div className="p-4">
                <PostList
                  posts={posts}
                  isLoading={postsLoading}
                  hasMore={false}
                />
              </div>
            </TabPane>

            <TabPane tab="粉丝" itemKey="followers">
              <div className="p-4 text-center text-gray-400 py-12">
                {useMock ? '（演示数据 — 连接后端后显示真实粉丝列表）' : '暂无粉丝'}
              </div>
            </TabPane>

            <TabPane tab="正在关注" itemKey="following">
              <div className="p-4 text-center text-gray-400 py-12">
                {useMock ? '（演示数据 — 连接后端后显示真实关注列表）' : '还没有关注任何人'}
              </div>
            </TabPane>
          </Tabs>
        </div>
      </div>
    </MainLayout>
  );
}
