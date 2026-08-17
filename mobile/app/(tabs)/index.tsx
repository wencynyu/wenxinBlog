import { useMemo } from 'react';
import { ActivityIndicator, RefreshControl, Text, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { useInfinitePosts } from '@/hooks/usePosts';
import { PostCard, PostCardGap } from '@/components/PostCard';
import { Spinner } from '@/components/Spinner';
import { ErrorState } from '@/components/ErrorState';
import { EmptyState } from '@/components/EmptyState';
import { getErrorMessage } from '@/lib/api/client';

export default function HomeScreen() {
  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
    isRefetching,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfinitePosts();

  const posts = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);

  if (isLoading) {
    return <Spinner label="正在加载博文…" />;
  }

  if (isError) {
    return <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />;
  }

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
        <RefreshControl refreshing={isRefetching} onRefresh={() => refetch()} tintColor="#0077fa" />
      }
      onEndReached={() => {
        if (hasNextPage) fetchNextPage();
      }}
      onEndReachedThreshold={0.3}
      ListEmptyComponent={
        <View className="px-4">
          <EmptyState title="还没有内容" subtitle="下拉刷新试试，或者注册账号写下第一篇博文" />
        </View>
      }
      ListFooterComponent={
        isFetchingNextPage ? (
          <View className="items-center py-4">
            <ActivityIndicator size="small" color="#0077fa" />
          </View>
        ) : posts.length > 0 ? (
          <Text className="px-4 py-4 text-center text-xs text-ink-faint">— 到底了 —</Text>
        ) : null
      }
    />
  );
}
