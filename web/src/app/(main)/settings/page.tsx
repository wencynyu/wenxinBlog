'use client';

import { useState, useEffect, useRef } from 'react';
import { Form, Input, Button, Toast, TagInput, Skeleton, Typography } from '@douyinfe/semi-ui';
import MainLayout from '@/components/layout/MainLayout';
import { useAuthStore } from '@/store/authStore';
import { useUpdateProfile, useUserProfile } from '@/hooks/useUser';
import { getUserInterests, updateUserInterests } from '@/lib/api/recommend';
import { listOAuthLinks, linkOAuth, unlinkOAuth } from '@/lib/api/auth';
import type { OAuthAccountItem } from '@/types/auth';

const { Title } = Typography;

export default function SettingsPage() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const updateProfile = useUpdateProfile(user?.id || '');
  // authStore.user 来自登录响应（只有 id/username/email/avatar），缺 displayName/bio/location/website。
  // 这些字段在 user-service 的 profile 里，必须拉 profile 才能回填表单做修改。
  const { data: profile } = useUserProfile(user?.id || '');

  const [interests, setInterests] = useState<string[]>([]);
  const [interestsLoading, setInterestsLoading] = useState(true);
  const [oauthLinks, setOauthLinks] = useState<OAuthAccountItem[]>([]);
  const [oauthLoading, setOauthLoading] = useState(true);
  const formRef = useRef<any>(null);

  useEffect(() => {
    if (profile && formRef.current) {
      formRef.current.setValues({
        displayName: profile.displayName || '',
        bio: profile.bio || '',
        avatar: profile.avatar || '',
        location: profile.location || '',
        website: profile.website || '',
      });
    }
  }, [profile]);

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

  const reloadOAuthLinks = () => {
    setOauthLoading(true);
    listOAuthLinks()
      .then(setOauthLinks)
      .catch(() => {})
      .finally(() => setOauthLoading(false));
  };

  useEffect(() => {
    if (user?.id) reloadOAuthLinks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  const handleBind = async (provider: string) => {
    try {
      const { authUrl } = await linkOAuth(provider);
      window.location.href = authUrl;
    } catch (error: any) {
      Toast.error(error?.message || '发起绑定失败');
    }
  };

  const handleUnbind = async (provider: string) => {
    try {
      await unlinkOAuth(provider);
      Toast.success('已解绑');
      reloadOAuthLinks();
    } catch (error: any) {
      Toast.error(error?.message || '解绑失败');
    }
  };

  const PROVIDER_LABEL: Record<string, string> = { google: 'Google' };
  const SUPPORTED = ['google'];

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

        {/* 第三方账号绑定 */}
        <div className="bg-surface rounded-xl shadow-card p-6 mt-6">
          <Title heading={4} className="font-serif text-ink mb-4">
            第三方账号
          </Title>
          <p className="text-ink-muted text-sm mb-4">
            绑定后可使用对应第三方账号直接登录。每个第三方身份仅可绑定一个本站账号。
          </p>
          {oauthLoading ? (
            <Skeleton.Paragraph style={{ width: '100%' }} />
          ) : (
            <div className="space-y-3">
              {SUPPORTED.map((provider) => {
                const linked = oauthLinks.find((l) => l.provider === provider);
                return (
                  <div
                    key={provider}
                    className="flex items-center justify-between border border-hairline rounded-lg px-4 py-3"
                  >
                    <span className="text-ink font-medium">
                      {PROVIDER_LABEL[provider] || provider}
                    </span>
                    {linked ? (
                      <Button theme="light" type="danger" onClick={() => handleUnbind(provider)}>
                        解绑
                      </Button>
                    ) : (
                      <Button theme="solid" onClick={() => handleBind(provider)}>
                        绑定
                      </Button>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
