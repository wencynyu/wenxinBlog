'use client';

import { useState } from 'react';
import { Tabs, TabPane, Avatar, Button, Toast } from '@douyinfe/semi-ui';
import { IconUserAdd, IconDelete } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import { useAuthStore } from '@/store/authStore';
import { useFollowUser, useUnfollowUser } from '@/hooks/useUser';
import type { UserProfile } from '@/types/user';
import type { Post } from '@/types/post';

interface UserProfileViewProps {
  userId: string;
  profile: UserProfile;
  posts: Post[];
}

export default function UserProfileView({ userId, profile, posts }: UserProfileViewProps) {
  const currentUser = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const follow = useFollowUser();
  const unfollow = useUnfollowUser();
  const [isFollowing, setIsFollowing] = useState(profile.isFollowing);
  const [activeTab, setActiveTab] = useState('posts');

  const isSelf = currentUser?.id === userId;
  const pending = follow.isPending || unfollow.isPending;

  const handleToggleFollow = async () => {
    if (!isAuthenticated) {
      Toast.warning('请先登录');
      return;
    }
    try {
      if (isFollowing) {
        await unfollow.mutateAsync(userId);
        setIsFollowing(false);
        Toast.success('已取消关注');
      } else {
        await follow.mutateAsync(userId);
        setIsFollowing(true);
        Toast.success('关注成功');
      }
    } catch (error: any) {
      Toast.error(error?.message || '操作失败');
    }
  };

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-3xl mx-auto">
        {/* 用户信息卡片 */}
        <div className="bg-surface rounded-xl shadow-card p-8 mb-6">
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
                <h3 className="font-serif text-2xl font-bold text-ink mb-1">
                  {profile.displayName || profile.username}
                </h3>
                <span className="text-ink-faint font-mono">@{profile.username}</span>
                {profile.bio && <p className="text-ink-muted mt-2">{profile.bio}</p>}
                {profile.location && (
                  <p className="text-ink-faint text-sm mt-1">{profile.location}</p>
                )}
              </div>
            </div>

            {!isSelf && (
              <Button
                theme={isFollowing ? 'borderless' : 'solid'}
                icon={isFollowing ? <IconDelete /> : <IconUserAdd />}
                onClick={handleToggleFollow}
                loading={pending}
              >
                {isFollowing ? '已关注' : '关注'}
              </Button>
            )}
          </div>

          {/* 统计 */}
          <div className="flex gap-8 mt-6 pt-6 border-t border-hairline">
            <div className="text-center">
              <span className="text-xl font-bold text-ink block font-mono">
                {profile.postsCount || 0}
              </span>
              <span className="text-ink-faint text-sm">博文</span>
            </div>
            <div className="text-center">
              <span className="text-xl font-bold text-ink block font-mono">
                {profile.followersCount || 0}
              </span>
              <span className="text-ink-faint text-sm">粉丝</span>
            </div>
            <div className="text-center">
              <span className="text-xl font-bold text-ink block font-mono">
                {profile.followingCount || 0}
              </span>
              <span className="text-ink-faint text-sm">关注</span>
            </div>
          </div>
        </div>

        {/* Tab 切换 */}
        <div className="bg-surface rounded-xl shadow-card">
          <Tabs activeKey={activeTab} onChange={setActiveTab} type="line">
            <TabPane tab="博文" itemKey="posts">
              <div className="p-4">
                {posts.length === 0 ? (
                  <EmptyState title="暂无博文" />
                ) : (
                  <PostList posts={posts} isLoading={false} hasMore={false} />
                )}
              </div>
            </TabPane>
            <TabPane tab="粉丝" itemKey="followers">
              <div className="p-4">
                <EmptyState title="暂无粉丝" description="粉丝列表待接入" />
              </div>
            </TabPane>
            <TabPane tab="正在关注" itemKey="following">
              <div className="p-4">
                <EmptyState title="还没有关注任何人" description="关注列表待接入" />
              </div>
            </TabPane>
          </Tabs>
        </div>
      </div>
    </MainLayout>
  );
}
