import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, Text, View } from 'react-native';
import { useAuthStore } from '@/store/authStore';
import { useUpdateProfile, useUserProfile } from '@/hooks/useUser';
import { FormInput } from '@/components/FormInput';
import { PrimaryButton } from '@/components/PrimaryButton';
import { getErrorMessage } from '@/lib/api/client';
import type { UserProfile, UpdateProfileRequest } from '@/types/user';

interface ProfileFormFields {
  displayName: string;
  bio: string;
  location: string;
  website: string;
}

/**
 * 表单以 profile 数据为 key 挂载：数据加载完成（或保存后刷新）时重新
 * 初始化 state，实现"服务端数据回填表单"，避免 effect 里同步 setState。
 */
function ProfileForm({
  profile,
  fallbackUsername,
  saving,
  saveError,
  onSave,
}: {
  profile: UserProfile | undefined;
  fallbackUsername: string;
  saving: boolean;
  saveError: string | null;
  onSave: (fields: ProfileFormFields) => void;
}) {
  const [displayName, setDisplayName] = useState(profile?.displayName ?? '');
  const [bio, setBio] = useState(profile?.bio ?? '');
  const [location, setLocation] = useState(profile?.location ?? '');
  const [website, setWebsite] = useState(profile?.website ?? '');

  const dirty =
    !!profile &&
    (displayName !== (profile.displayName ?? '') ||
      bio !== (profile.bio ?? '') ||
      location !== (profile.location ?? '') ||
      website !== (profile.website ?? ''));

  const save = () => {
    onSave({ displayName, bio, location, website });
  };

  return (
    <View className="gap-4 rounded-2xl border border-hairline bg-surface p-4">
      <FormInput
        label="昵称"
        value={displayName}
        onChangeText={setDisplayName}
        placeholder={fallbackUsername}
        autoCapitalize="sentences"
      />
      <FormInput
        label="简介"
        value={bio}
        onChangeText={setBio}
        placeholder="一句话介绍自己"
        autoCapitalize="sentences"
      />
      <FormInput
        label="所在地"
        value={location}
        onChangeText={setLocation}
        placeholder="例如：上海"
        autoCapitalize="sentences"
      />
      <FormInput
        label="个人网站"
        value={website}
        onChangeText={setWebsite}
        placeholder="https://"
      />

      {saveError ? (
        <Text className="text-sm" style={{ color: '#ef4444' }}>
          {saveError}
        </Text>
      ) : null}
      <PrimaryButton label="保存修改" onPress={save} loading={saving} disabled={!dirty} />
    </View>
  );
}

export default function SettingsScreen() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  // /auth/me 只返回基础字段（id/username/email），昵称/简介等资料
  // 必须从 user-service 的完整画像（/users/{id}）取
  const profileQuery = useUserProfile(user?.id ?? '');
  const update = useUpdateProfile(user?.id ?? '');

  if (!user) {
    return null;
  }

  const save = (fields: ProfileFormFields) => {
    const data: UpdateProfileRequest = {
      displayName: fields.displayName.trim() || undefined,
      bio: fields.bio.trim() || undefined,
      location: fields.location.trim() || undefined,
      website: fields.website.trim() || undefined,
    };
    update.mutate(data, {
      onSuccess: (profile) => {
        // 回写 authStore，让"我的"页同步最新资料
        setUser(profile);
        Alert.alert('已保存', '个人资料已更新');
      },
    });
  };

  return (
    <ScrollView
      className="flex-1 bg-canvas"
      contentContainerClassName="gap-4 px-4 pb-8 pt-4"
      keyboardShouldPersistTaps="handled"
    >
      <View className="flex-row items-center justify-between px-1">
        <Text className="text-base font-bold text-ink">编辑资料</Text>
        {profileQuery.isLoading ? <ActivityIndicator size="small" color="#0077fa" /> : null}
      </View>

      {profileQuery.isError ? (
        <Text className="px-1 text-[13px] text-ink-faint">资料加载失败，仍可直接填写并保存</Text>
      ) : null}

      <ProfileForm
        key={String(profileQuery.dataUpdatedAt)}
        profile={profileQuery.data}
        fallbackUsername={user.username}
        saving={update.isPending}
        saveError={update.isError ? getErrorMessage(update.error, '保存失败，请稍后重试') : null}
        onSave={save}
      />

      <View className="gap-2 rounded-2xl border border-hairline bg-surface p-4">
        <Text className="text-base font-bold text-ink">关于</Text>
        <View className="flex-row items-center justify-between">
          <Text className="text-sm text-ink-muted">版本</Text>
          <Text className="text-sm text-ink-faint">1.0.0</Text>
        </View>
        <Text className="text-xs leading-5 text-ink-faint">
          WenxinBlog Mobile · Expo SDK 57 · 深色模式跟随系统设置
        </Text>
      </View>
    </ScrollView>
  );
}
