import { Pressable, ScrollView, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useAuthStore } from '@/store/authStore';
import { useLogout } from '@/hooks/useAuth';
import { useUserProfile } from '@/hooks/useUser';
import { usePosts } from '@/hooks/usePosts';
import { Avatar } from '@/components/Avatar';
import { Spinner } from '@/components/Spinner';
import { formatCount } from '@/lib/format';

export default function ProfileScreen() {
  const router = useRouter();
  const authUser = useAuthStore((s) => s.user);
  const logout = useLogout();
  const profileQuery = useUserProfile(authUser?.id ?? '');
  // 只为拿真实博文 total（user-service 的 postsCount 无写入方，恒为 0）
  const postsQuery = usePosts({ authorId: authUser?.id ?? '', status: 'published', pageSize: 1 });

  if (!authUser) {
    return <Spinner />;
  }

  // 展示态优先用 user-service 的完整画像（粉丝/关注/博文数），失败则退回 authStore 的基础信息
  const profile = profileQuery.data;

  return (
    <ScrollView className="flex-1 bg-canvas" contentContainerClassName="px-6 py-8">
      <View className="flex-row items-center gap-4">
        <Avatar user={authUser} size={64} />
        <View className="gap-1">
          <Text className="text-xl font-bold text-ink">
            {authUser.displayName || authUser.username}
          </Text>
          <Text className="text-sm text-ink-faint">@{authUser.username}</Text>
          <Text className="text-sm text-ink-faint" numberOfLines={1}>
            {authUser.email}
          </Text>
        </View>
      </View>

      {authUser.bio ? <Text className="mt-4 text-sm text-ink-muted">{authUser.bio}</Text> : null}

      {/* 粉丝 / 关注 / 博文数（user-service） */}
      {profileQuery.isLoading ? (
        <Text className="mt-6 text-sm text-ink-faint">加载统计数据…</Text>
      ) : profileQuery.isError ? (
        <Text className="mt-6 text-sm text-ink-faint">统计数据暂不可用</Text>
      ) : profile ? (
        <View className="mt-6 flex-row gap-8">
          <Stat label="关注" value={formatCount(profile.followingCount)} />
          <Stat label="粉丝" value={formatCount(profile.followersCount)} />
          <Stat
            label="博文"
            value={formatCount(postsQuery.data?.total ?? profile.postsCount ?? 0)}
          />
        </View>
      ) : null}

      <View className="mt-6 gap-2">
        <InfoRow label="加入时间" value={authUser.createdAt?.slice(0, 10) ?? '-'} />
        <LinkRow label="我的主页" onPress={() => router.push(`/user/${authUser.id}`)} />
        <LinkRow label="编辑资料" onPress={() => router.push('/settings')} />
      </View>

      <Pressable
        onPress={() => logout.mutate()}
        disabled={logout.isPending}
        className="mt-8 items-center rounded-lg border border-hairline bg-surface py-3 active:opacity-70 disabled:opacity-50"
        accessibilityRole="button"
        accessibilityLabel="退出登录"
      >
        <Text className="text-base font-medium" style={{ color: '#ef4444' }}>
          {logout.isPending ? '退出中…' : '退出登录'}
        </Text>
      </Pressable>
    </ScrollView>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View className="items-center gap-0.5">
      <Text className="text-lg font-bold text-ink">{value}</Text>
      <Text className="text-xs text-ink-faint">{label}</Text>
    </View>
  );
}

function InfoRow({ label, value }: { label: string; value?: string }) {
  return (
    <View className="flex-row items-center justify-between rounded-lg border border-hairline bg-surface px-4 py-3">
      <Text className="text-sm text-ink">{label}</Text>
      <Text className="text-sm text-ink-faint">{value}</Text>
    </View>
  );
}

function LinkRow({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      className="flex-row items-center justify-between rounded-lg border border-hairline bg-surface px-4 py-3 active:opacity-70"
      accessibilityRole="button"
      accessibilityLabel={label}
    >
      <Text className="text-sm text-ink">{label}</Text>
      <Ionicons name="chevron-forward" size={16} color="#c3c8cf" />
    </Pressable>
  );
}
