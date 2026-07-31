'use client';

import { useState, useEffect, useRef } from 'react';
import { Form, Input, Button, Toast, TagInput, Skeleton, Typography } from '@douyinfe/semi-ui';
import MainLayout from '@/components/layout/MainLayout';
import { useAuthStore } from '@/store/authStore';
import { useUpdateProfile } from '@/hooks/useUser';
import { getUserInterests, updateUserInterests } from '@/lib/api/recommend';

const { Title } = Typography;

export default function SettingsPage() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const updateProfile = useUpdateProfile(user?.id || '');

  const [interests, setInterests] = useState<string[]>([]);
  const [interestsLoading, setInterestsLoading] = useState(true);
  const formRef = useRef<any>(null);

  useEffect(() => {
    if (user && formRef.current) {
      formRef.current.setValues({
        displayName: user.displayName || '',
        bio: user.bio || '',
        avatar: user.avatar || '',
        location: '',
        website: '',
      });
    }
  }, [user]);

  useEffect(() => {
    if (user?.id) {
      getUserInterests()
        .then((tags) => {
          setInterests(tags.map((t: any) => t.tag));
        })
        .catch(() => {})
        .finally(() => setInterestsLoading(false));
    }
  }, [user?.id]);

  const handleSaveProfile = async (values: any) => {
    try {
      const updated = await updateProfile.mutateAsync({
        displayName: values.displayName || undefined,
        bio: values.bio || undefined,
        avatar: values.avatar || undefined,
        location: values.location || undefined,
        website: values.website || undefined,
      });
      if (setUser && user) {
        // 修复：后端返回的 user_id 才是真实用户 id（id 是 user_profiles 主键），字段是 snake_case。
        // 直接 setUser(updated) 会把 profile 主键塞进 user.id，导致「我的主页」跳转到不存在的用户 id。
        const up = updated as any;
        setUser({
          ...user,
          id: up.user_id || user.id,
          displayName: up.display_name ?? user.displayName,
          avatar: up.avatar_url ?? user.avatar,
          bio: up.bio ?? user.bio,
          location: up.location ?? user.location,
          website: up.website ?? user.website,
        });
      }
      Toast.success('个人资料已更新');
    } catch (error: any) {
      Toast.error(error?.message || '更新失败');
    }
  };

  const handleSaveInterests = async () => {
    if (!user?.id) return;
    try {
      await updateUserInterests(interests);
      Toast.success('兴趣标签已更新');
    } catch (error: any) {
      Toast.error(error?.message || '更新失败');
    }
  };

  if (!user) {
    return (
      <MainLayout>
        <div className="max-w-2xl mx-auto text-center py-20">
          <Title heading={3} className="text-ink">
            请先登录
          </Title>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-2xl mx-auto">
        <p className="eyebrow mb-2">{'// settings'}</p>
        <Title heading={2} className="font-serif text-ink mb-6">
          个人设置
        </Title>

        {/* 个人信息 */}
        <div className="bg-surface rounded-xl shadow-card p-6 mb-6">
          <Title heading={4} className="font-serif text-ink mb-4">
            个人资料
          </Title>
          <Form
            onSubmit={handleSaveProfile}
            getFormApi={(api: any) => {
              formRef.current = api;
            }}
          >
            <Form.Input
              field="displayName"
              label="昵称"
              placeholder="请输入昵称"
              rules={[{ max: 30, message: '昵称最多30个字符' }]}
            />
            <Form.Input
              field="bio"
              label="个人简介"
              placeholder="介绍一下自己..."
              maxLength={200}
              showClear
            />
            <Form.Input field="avatar" label="头像链接" placeholder="输入头像图片URL" />
            <Form.Input field="location" label="所在地" placeholder="例如：北京" />
            <Form.Input field="website" label="个人网站" placeholder="https://example.com" />
            <Button
              theme="solid"
              htmlType="submit"
              loading={updateProfile.isPending}
              style={{ marginTop: 16 }}
            >
              保存资料
            </Button>
          </Form>
        </div>

        {/* 兴趣标签 */}
        <div className="bg-surface rounded-xl shadow-card p-6">
          <Title heading={4} className="font-serif text-ink mb-4">
            兴趣标签
          </Title>
          <p className="text-ink-muted text-sm mb-4">选择你感兴趣的标签，获得更精准的推荐</p>
          {interestsLoading ? (
            <Skeleton.Paragraph style={{ width: '100%' }} />
          ) : (
            <>
              <TagInput
                value={interests}
                onChange={setInterests as any}
                placeholder="添加兴趣标签..."
                max={10}
                style={{ width: '100%' }}
              />
              <Button theme="solid" onClick={handleSaveInterests} style={{ marginTop: 16 }}>
                保存标签
              </Button>
            </>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
