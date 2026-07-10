import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { useInfiniteQuery } from '@tanstack/react-query';

const API_URL = 'http://localhost:8080';

interface Post {
  id: string;
  title: string;
  summary: string;
  author: { displayName: string };
  viewCount: number;
  likeCount: number;
}

export default function HomeTab() {
  const router = useRouter();

  const { data, fetchNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['posts', 'feed'],
    queryFn: ({ pageParam = 1 }) =>
      fetch(`${API_URL}/api/v1/posts?page=${pageParam}&pageSize=10`).then(r => r.json()),
    getNextPageParam: (_lastPage, pages) => pages.length + 1,
    initialPageParam: 1,
  });

  const posts = data?.pages.flatMap(p => p.items ?? []) ?? [];

  const renderItem = ({ item }: { item: Post }) => (
    <TouchableOpacity style={styles.postCard} onPress={() => router.push(`/posts/${item.id}`)}>
      <Text style={styles.authorName}>{item.author?.displayName || '匿名'}</Text>
      <Text style={styles.postTitle}>{item.title}</Text>
      {item.summary ? <Text style={styles.postSummary} numberOfLines={2}>{item.summary}</Text> : null}
      <View style={styles.postFooter}>
        <Text style={styles.stat}>👁 {item.viewCount}</Text>
        <Text style={styles.stat}>❤️ {item.likeCount}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <FlatList
      data={posts}
      renderItem={renderItem}
      keyExtractor={item => item.id}
      onEndReached={() => fetchNextPage()}
      onEndReachedThreshold={0.5}
      refreshing={isFetchingNextPage}
      contentContainerStyle={styles.list}
      ListEmptyComponent={<Text style={styles.empty}>暂无内容</Text>}
    />
  );
}

const styles = StyleSheet.create({
  list: { padding: 16 },
  postCard: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 12, shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 8, elevation: 2 },
  authorName: { fontSize: 14, fontWeight: '600', color: '#333', marginBottom: 8 },
  postTitle: { fontSize: 18, fontWeight: 'bold', color: '#111', marginBottom: 6 },
  postSummary: { fontSize: 14, color: '#666', lineHeight: 20 },
  postFooter: { flexDirection: 'row', gap: 16, marginTop: 10 },
  stat: { fontSize: 12, color: '#999' },
  empty: { textAlign: 'center', color: '#999', marginTop: 60 },
});
