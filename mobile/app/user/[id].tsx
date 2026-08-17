import { Pressable, RefreshControl, Text, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuthStore } from '@/store/authStore';
import { useUserProfile, useFollowUser, useUnfollowUser } from '@/hooks/useUser';
import { usePosts } from '@/hooks/usePosts';
import { Avatar } from '@/components/Avatar';
import { PostCard, PostCardGap } from '@/components/PostCard';
import { Spinner } from '@/components/Spinner';
import { ErrorState } from '@/components/ErrorState';
import { EmptyState } from '@/components/EmptyState';
import { formatCount } from '@/lib/format';

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View className="items-center gap-0.5">
      <Text className="text-lg font-bold text-ink">{value}</Text>
      <Text className="text-xs text-ink-faint">{label}</Text>
    </View>
  );
}

export default function UserProfileScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const myId = useAuthStore((s) => s.user?.id);
  const isSelf = myId === id;

  const profileQuery = useUserProfile(id);
  const postsQuery = usePosts({ authorId: id, status: 'published', pageSize: 20 });
  const follow = useFollowUser();
  const unfollow = useUnfollowUser();

  if (profileQuery.isLoading) {
    return <Spinner label="正在加载用户主页…" />;
  }

  if (profileQuery.isError || !profileQuery.data) {
    return <ErrorState message="用户不存在或加载失败" onRetry={() => profileQuery.refetch()} />;
  }

  const profile = profileQuery.data;
  const posts = postsQuery.data?.items ?? [];
  const followPending = follow.isPending || unfollow.isPending;

  // 博文数用 blog-service 的真实 total（user-service 的 postsCount 无写入方，恒为 0，与 web 端做法一致）
  const postTotal = postsQuery.data?.total ?? profileQuery.data?.postsCount ?? 0;

  return (
    <FlashList
      data={posts}
      keyExtractor={(item) => item.id}
      renderItem={({ item }) => (
        <View className="px-4">
          <PostCard post={item} />
        </View>
      )}
      ItemSeparatorComponent={PostCardGap}
      refreshControl={
        <RefreshControl
          refreshing={profileQuery.isRefetching || postsQuery.isRefetching}
          onRefresh={() => {
            profileQuery.refetch();
            postsQuery.refetch();
          }}
          tintColor="#0077fa"
        />
      }
      ListHeaderComponent={
        <View className="gap-4 px-4 pb-5 pt-1">
          <View className="flex-row items-center gap-4">
            <Avatar
              user={{
                username: profile.username,
                displayName: profile.displayName,
                avatar: profile.avatar,
              }}
              size={68}
            />
            <View className="flex-1 gap-0.5">
              <Text className="text-xl font-bold text-ink">
                {profile.displayName || profile.username}
              </Text>
              <Text className="text-sm text-ink-faint">@{profile.username}</Text>
            </View>
            {isSelf ? (
              <Pressable
                onPress={() => router.push('/settings')}
                className="rounded-lg border border-primary px-4 py-2 active:opacity-70"
                accessibilityRole="button"
                accessibilityLabel="编辑资料"
              >
                <Text className="text-sm font-medium text-primary">编辑资料</Text>
              </Pressable>
            ) : (
              <Pressable
                onPress={() => (profile.isFollowing ? unfollow.mutate(id) : follow.mutate(id))}
                disabled={followPending}
                className={`rounded-lg px-4 py-2 active:opacity-70 ${profile.isFollowing ? 'border border-hairline bg-surface' : 'bg-primary'}`}
                accessibilityRole="button"
                accessibilityLabel={profile.isFollowing ? '取消关注' : '关注'}
                accessibilityState={{ disabled: followPending }}
              >
                <Text
                  className={`text-sm font-medium ${profile.isFollowing ? 'text-ink-muted' : 'text-white'}`}
                >
                  {followPending ? '…' : profile.isFollowing ? '已关注' : '+ 关注'}
                </Text>
              </Pressable>
            )}
          </View>

          {profile.bio ? (
            <Text className="text-sm leading-relaxed text-ink-muted">{profile.bio}</Text>
          ) : null}

          <View className="flex-row gap-8">
            <Stat label="关注" value={formatCount(profile.followingCount)} />
            <Stat label="粉丝" value={formatCount(profile.followersCount)} />
            <Stat label="博文" value={formatCount(postTotal)} />
          </View>

          <View className="border-t border-hairline pt-4">
            <Text className="text-base font-bold text-ink">TA 的博文</Text>
          </View>
        </View>
      }
      ListEmptyComponent={
        postsQuery.isLoading ? null : (
          <View className="px-4">
            <EmptyState title="还没有发布过博文" subtitle={isSelf ? '去写第一篇吧' : undefined} />
          </View>
        )
      }
    />
  );
}
