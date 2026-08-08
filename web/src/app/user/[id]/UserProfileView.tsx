'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Tabs,
  TabPane,
  Avatar,
  Button,
  Toast,
  Card,
  Typography,
  Skeleton,
} from '@douyinfe/semi-ui';
import { IconUserAdd, IconDelete } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import PostList from '@/components/post/PostList';
import EmptyState from '@/components/common/EmptyState';
import { useAuthStore } from '@/store/authStore';
import { useUserProfile, useFollowUser, useUnfollowUser } from '@/hooks/useUser';
import { usePosts } from '@/hooks/usePosts';

interface UserProfileViewProps {
  userId: string;
}

export default function UserProfileView({ userId }: UserProfileViewProps) {
  const router = useRouter();
  const currentUser = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const follow = useFollowUser();
  const unfollow = useUnfollowUser();

  const { data: profile, isLoading } = useUserProfile(userId);
  const { data: postsResp } = usePosts({
    authorId: userId,
    status: 'published',
    sortBy: 'createdAt',
    sortOrder: 'desc',
    page: 1,
    pageSize: 10,
  });

  const [isFollowing, setIsFollowing] = useState(false);
  const [activeTab, setActiveTab] = useState('posts');

  // profile 到达后同步一次关注状态（关注/取关后 react-query 会刷新 profile）
  const profileFollowing = profile?.isFollowing;
  if (profileFollowing !== undefined && profileFollowing !== isFollowing) {
    setIsFollowing(profileFollowing);
  }

  if (isLoading) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto">
          <Card shadows="hover" bodyStyle={{ padding: 32 }} style={{ marginBottom: 24 }}>
            <div className="flex items-center gap-6">
              <Skeleton.Avatar size="extra-large" />
              <div className="space-y-2">
                <Skeleton.Title style={{ width: 128, height: 24 }} />
                <Skeleton.Title style={{ width: 96, height: 16 }} />
              </div>
            </div>
          </Card>
        </div>
      </MainLayout>
    );
  }

  if (!profile) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto py-20">
          <EmptyState
            title="用户不存在"
            description="该用户可能不存在，或后端服务暂不可用"
            actionText="返回首页"
            onAction={() => router.push('/')}
          />
        </div>
      </MainLayout>
    );
  }

  const posts = postsResp?.items ?? [];
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
        <Card shadows="hover" bodyStyle={{ padding: 32 }} style={{ marginBottom: 24 }}>
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
                <Typography.Title heading={3} style={{ marginBottom: 4 }}>
                  {profile.displayName || profile.username}
                </Typography.Title>
                <Typography.Text type="tertiary" className="font-mono">
                  @{profile.username}
                </Typography.Text>
                {profile.bio && (
                  <Typography.Paragraph type="tertiary" style={{ marginTop: 8, marginBottom: 0 }}>
                    {profile.bio}
                  </Typography.Paragraph>
                )}
                {profile.location && (
                  <Typography.Text
                    type="tertiary"
                    size="small"
                    style={{ display: 'block', marginTop: 4 }}
                  >
                    {profile.location}
                  </Typography.Text>
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
              <Typography.Text strong style={{ fontSize: 24, display: 'block' }}>
                {postsResp?.total ?? 0}
              </Typography.Text>
              <Typography.Text type="tertiary">博文</Typography.Text>
            </div>
            <div className="text-center">
              <Typography.Text strong style={{ fontSize: 24, display: 'block' }}>
                {profile.followersCount || 0}
              </Typography.Text>
              <Typography.Text type="tertiary">粉丝</Typography.Text>
            </div>
            <div className="text-center">
              <Typography.Text strong style={{ fontSize: 24, display: 'block' }}>
                {profile.followingCount || 0}
              </Typography.Text>
              <Typography.Text type="tertiary">关注</Typography.Text>
            </div>
          </div>
        </Card>

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
