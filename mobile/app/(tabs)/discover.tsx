import { Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTrendingPosts, useFeedRecommendations } from '@/hooks/useRecommendations';
import { PostCard } from '@/components/PostCard';
import { Spinner } from '@/components/Spinner';
import { ErrorState } from '@/components/ErrorState';
import { formatCount } from '@/lib/format';
import type { TrendingPost } from '@/lib/api/recommend';
import { useRouter } from 'expo-router';

function SectionTitle({ icon, title }: { icon: keyof typeof Ionicons.glyphMap; title: string }) {
  return (
    <View className="flex-row items-center gap-1.5 pb-3">
      <Ionicons name={icon} size={17} color="#e08600" />
      <Text className="text-base font-bold text-ink">{title}</Text>
    </View>
  );
}

function TrendingRow({ post, rank }: { post: TrendingPost; rank: number }) {
  const router = useRouter();
  const topThree = rank <= 3;

  return (
    <Pressable
      onPress={() => router.push(`/posts/${post.id}`)}
      className="flex-row items-center gap-3 py-2.5 active:opacity-60"
      accessibilityRole="button"
      accessibilityLabel={`热榜第 ${rank} 名：${post.title}`}
    >
      <Text
        className="w-6 text-center text-lg font-bold italic"
        style={{ color: topThree ? '#e08600' : '#8a919c' }}
      >
        {rank}
      </Text>
      <View className="flex-1 gap-0.5">
        <Text className="text-[15px] font-medium leading-snug text-ink" numberOfLines={2}>
          {post.title}
        </Text>
        <Text className="text-xs text-ink-faint">
          {formatCount(post.viewsCount)} 阅读 · {formatCount(post.likeCount)} 赞
        </Text>
      </View>
      <Ionicons name="chevron-forward" size={16} color="#c3c8cf" />
    </Pressable>
  );
}

export default function DiscoverScreen() {
  const trending = useTrendingPosts(10);
  const feed = useFeedRecommendations({ size: 10 });
  const refreshing = trending.isRefetching || feed.isRefetching;

  if (trending.isLoading) {
    return <Spinner label="正在发现好内容…" />;
  }

  if (trending.isError) {
    return (
      <ErrorState
        message="推荐服务暂不可用"
        onRetry={() => {
          trending.refetch();
          feed.refetch();
        }}
      />
    );
  }

  return (
    <ScrollView
      className="flex-1 bg-canvas"
      contentContainerClassName="px-4 pb-8 pt-2"
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={() => {
            trending.refetch();
            feed.refetch();
          }}
          tintColor="#0077fa"
        />
      }
    >
      <View className="rounded-2xl border border-hairline bg-surface px-4 py-2">
        <SectionTitle icon="flame" title="热榜" />
        {(trending.data ?? []).map((post, index) => (
          <TrendingRow key={post.id} post={post} rank={index + 1} />
        ))}
        {(trending.data?.length ?? 0) === 0 ? (
          <Text className="py-4 text-center text-sm text-ink-faint">暂无热榜数据</Text>
        ) : null}
      </View>

      <View className="mt-5">
        <SectionTitle icon="sparkles" title="为你推荐" />
        {feed.isLoading ? (
          <Text className="py-4 text-center text-sm text-ink-faint">推荐加载中…</Text>
        ) : feed.isError ? (
          <Text className="py-4 text-center text-sm text-ink-faint">推荐流暂不可用</Text>
        ) : (
          <View className="gap-3">
            {(feed.data ?? []).map((item) => (
              <PostCard key={item.id} post={item} />
            ))}
          </View>
        )}
      </View>
    </ScrollView>
  );
}
