import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import {
  useSearchPosts,
  useSearchUsers,
  useTrendingSearches,
  useTrendingTags,
  useSearchHistory,
  useClearSearchHistory,
} from '@/hooks/useSearch';
import { PostCard } from '@/components/PostCard';
import { EmptyState } from '@/components/EmptyState';
import { Avatar } from '@/components/Avatar';
import { formatCount } from '@/lib/format';

function Chip({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      className="rounded-full border border-hairline bg-surface px-3 py-1.5 active:opacity-60"
      accessibilityRole="button"
      accessibilityLabel={`搜索 ${label}`}
    >
      <Text className="text-sm text-ink">{label}</Text>
    </Pressable>
  );
}

function UserRow({
  user,
  onPress,
}: {
  user: {
    id: string;
    username: string;
    displayName?: string;
    avatarUrl?: string;
    bio?: string;
    followerCount: number;
  };
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      className="flex-row items-center gap-3 rounded-2xl border border-hairline bg-surface p-3.5 active:opacity-70"
      accessibilityRole="button"
      accessibilityLabel={`用户：${user.displayName || user.username}`}
    >
      <Avatar
        user={{ username: user.username, displayName: user.displayName, avatar: user.avatarUrl }}
        size={40}
      />
      <View className="flex-1 gap-0.5">
        <Text className="text-[15px] font-semibold text-ink" numberOfLines={1}>
          {user.displayName || user.username}
        </Text>
        <Text className="text-xs text-ink-faint" numberOfLines={1}>
          @{user.username} · {formatCount(user.followerCount)} 粉丝
        </Text>
      </View>
      <Ionicons name="chevron-forward" size={16} color="#c3c8cf" />
    </Pressable>
  );
}

export default function SearchScreen() {
  const router = useRouter();
  const [input, setInput] = useState('');
  // 300ms 防抖后再触发请求
  const [query, setQuery] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), 300);
    return () => clearTimeout(timer);
  }, [input]);

  const trendingSearches = useTrendingSearches();
  const trendingTags = useTrendingTags();
  const history = useSearchHistory();
  const clearHistory = useClearSearchHistory();
  const posts = useSearchPosts(query);
  const users = useSearchUsers(query);

  const userItems = useMemo(() => users.data?.items ?? [], [users.data]);
  const postItems = useMemo(() => posts.data?.items ?? [], [posts.data]);
  const searching = query.length > 0;
  const resultCount = userItems.length + postItems.length;

  return (
    <SafeAreaView className="flex-1 bg-canvas" edges={['top']}>
      {/* 搜索栏 */}
      <View className="flex-row items-center gap-2.5 border-b border-hairline bg-surface px-4 py-2.5">
        <Pressable
          onPress={() => router.back()}
          hitSlop={8}
          accessibilityRole="button"
          accessibilityLabel="返回"
        >
          <Ionicons name="chevron-back" size={24} color="#1c1e21" />
        </Pressable>
        <View className="flex-1 flex-row items-center gap-2 rounded-xl bg-canvas px-3">
          <Ionicons name="search" size={16} color="#8a919c" />
          <TextInput
            value={input}
            onChangeText={setInput}
            placeholder="搜索博文、用户…"
            placeholderTextColor="#8a919c"
            autoFocus
            returnKeyType="search"
            className="flex-1 py-2.5 text-[15px] text-ink"
            accessibilityLabel="搜索输入框"
          />
          {input ? (
            <Pressable
              onPress={() => setInput('')}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel="清空搜索词"
            >
              <Ionicons name="close-circle" size={16} color="#8a919c" />
            </Pressable>
          ) : null}
        </View>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="取消搜索"
        >
          <Text className="text-[15px] text-ink-muted">取消</Text>
        </Pressable>
      </View>

      <ScrollView
        className="flex-1"
        contentContainerClassName="px-4 pb-8 pt-4"
        keyboardShouldPersistTaps="handled"
      >
        {searching ? (
          <>
            {posts.isLoading || users.isLoading ? (
              <View className="items-center py-10">
                <ActivityIndicator size="small" color="#0077fa" />
              </View>
            ) : resultCount === 0 ? (
              <EmptyState title={`没有找到「${query}」相关内容`} subtitle="换个关键词试试" />
            ) : (
              <View className="gap-3">
                {userItems.length > 0 ? (
                  <View className="gap-2.5">
                    <Text className="text-sm font-bold text-ink">用户</Text>
                    {userItems.map((user) => (
                      <UserRow
                        key={user.id}
                        user={user}
                        onPress={() => router.push(`/user/${user.id}`)}
                      />
                    ))}
                  </View>
                ) : null}
                {postItems.length > 0 ? (
                  <View className="gap-3">
                    <Text className="text-sm font-bold text-ink">博文</Text>
                    {postItems.map((post) => (
                      <PostCard key={post.id} post={post} />
                    ))}
                  </View>
                ) : null}
              </View>
            )}
          </>
        ) : (
          <View className="gap-6">
            {/* 搜索历史（登录后服务端记录） */}
            {(history.data?.length ?? 0) > 0 ? (
              <View className="gap-2.5">
                <View className="flex-row items-center justify-between">
                  <Text className="text-sm font-bold text-ink">搜索历史</Text>
                  <Pressable
                    onPress={() => clearHistory.mutate()}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel="清空搜索历史"
                  >
                    <Ionicons name="trash-outline" size={16} color="#8a919c" />
                  </Pressable>
                </View>
                <View className="flex-row flex-wrap gap-2">
                  {(history.data ?? []).map((keyword) => (
                    <Chip key={keyword} label={keyword} onPress={() => setInput(keyword)} />
                  ))}
                </View>
              </View>
            ) : null}

            <View className="gap-2.5">
              <Text className="text-sm font-bold text-ink">热门搜索</Text>
              <View className="flex-row flex-wrap gap-2">
                {(trendingSearches.data ?? []).map((keyword) => (
                  <Chip key={keyword} label={keyword} onPress={() => setInput(keyword)} />
                ))}
                {trendingSearches.data?.length === 0 ? (
                  <Text className="text-sm text-ink-faint">暂无数据</Text>
                ) : null}
              </View>
            </View>

            <View className="gap-2.5">
              <Text className="text-sm font-bold text-ink">热门标签</Text>
              <View className="flex-row flex-wrap gap-2">
                {(trendingTags.data ?? []).map((tag) => (
                  <Chip key={tag} label={`# ${tag}`} onPress={() => setInput(tag)} />
                ))}
                {trendingTags.data?.length === 0 ? (
                  <Text className="text-sm text-ink-faint">暂无数据</Text>
                ) : null}
              </View>
            </View>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
