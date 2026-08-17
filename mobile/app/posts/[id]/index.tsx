import { useMemo, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import Markdown from 'react-native-markdown-display';
import { Ionicons } from '@expo/vector-icons';
import { usePost, useToggleLike, useToggleFavorite } from '@/hooks/usePosts';
import { useComments, useCreateComment } from '@/hooks/useComments';
import { useRelatedPosts } from '@/hooks/useRecommendations';
import { useAuthStore } from '@/store/authStore';
import { Avatar } from '@/components/Avatar';
import { PostCard } from '@/components/PostCard';
import { Spinner } from '@/components/Spinner';
import { ErrorState } from '@/components/ErrorState';
import { EmptyState } from '@/components/EmptyState';
import { formatDate, formatCount } from '@/lib/format';
import { getErrorMessage } from '@/lib/api/client';
import type { Post } from '@/types/post';

function useMarkdownStyles() {
  const dark = useColorScheme() === 'dark';
  return useMemo(
    () =>
      StyleSheet.create({
        body: { color: dark ? '#e8eaed' : '#1c1e21', fontSize: 16, lineHeight: 26 },
        heading1: { color: dark ? '#ffffff' : '#111214', fontSize: 24, fontWeight: '700' },
        heading2: { color: dark ? '#ffffff' : '#111214', fontSize: 20, fontWeight: '700' },
        heading3: { color: dark ? '#f0f1f3' : '#33363a', fontSize: 18, fontWeight: '600' },
        link: { color: '#0077fa' },
        blockquoteText: { color: dark ? '#9aa3ad' : '#5c6470' },
        blockQuoteSection: {
          borderLeftColor: '#0077fa',
          borderLeftWidth: 3,
        },
        codeInline: {
          color: dark ? '#ffdd88' : '#bb6400',
          backgroundColor: dark ? '#2a2e33' : '#f0f2f5',
        },
        fence: {
          backgroundColor: dark ? '#101214' : '#f0f2f5',
          borderColor: dark ? '#2a2e33' : '#e5e7eb',
        },
        hr: { backgroundColor: dark ? '#2a2e33' : '#e5e7eb' },
      }),
    [dark],
  );
}

function ActionButton({
  icon,
  activeIcon,
  label,
  active,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  activeIcon: keyof typeof Ionicons.glyphMap;
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      className="flex-row items-center gap-1.5 rounded-lg border border-hairline bg-surface px-3.5 py-2 active:opacity-70"
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected: active }}
    >
      <Ionicons
        name={active ? activeIcon : icon}
        size={16}
        color={active ? '#0077fa' : '#5c6470'}
      />
      <Text className="text-sm" style={{ color: active ? '#0077fa' : '#5c6470' }}>
        {label}
      </Text>
    </Pressable>
  );
}

function CommentComposer({ postId }: { postId: string }) {
  const [draft, setDraft] = useState('');
  const createComment = useCreateComment(postId);

  const submit = () => {
    const content = draft.trim();
    if (!content || createComment.isPending) return;
    createComment.mutate(content, { onSuccess: () => setDraft('') });
  };

  return (
    <View className="flex-row items-end gap-2 border-t border-hairline bg-surface px-4 py-3">
      <TextInput
        value={draft}
        onChangeText={setDraft}
        placeholder="写下你的评论…"
        placeholderTextColor="#8a919c"
        multiline
        className="max-h-24 flex-1 rounded-lg border border-hairline bg-canvas px-3 py-2.5 text-ink text-base"
        accessibilityLabel="评论输入框"
      />
      <Pressable
        onPress={submit}
        disabled={!draft.trim() || createComment.isPending}
        className="items-center justify-center rounded-lg bg-primary px-4 py-3 active:opacity-70 disabled:opacity-40"
        accessibilityRole="button"
        accessibilityLabel="发送评论"
      >
        <Ionicons
          name={createComment.isPending ? 'hourglass-outline' : 'send'}
          size={18}
          color="#ffffff"
        />
      </Pressable>
    </View>
  );
}

export default function PostDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const myId = useAuthStore((s) => s.user?.id);
  const mdStyles = useMarkdownStyles();

  const postQuery = usePost(id);
  const like = useToggleLike();
  const favorite = useToggleFavorite();
  const commentsQuery = useComments(id);
  const relatedQuery = useRelatedPosts(postQuery.data?.id, 3);

  const post: Post | undefined = postQuery.data;
  const isOwnPost = !!post && myId === post.authorId;

  if (postQuery.isLoading) {
    return <Spinner label="正在加载博文…" />;
  }

  if (postQuery.isError || !post) {
    return (
      <ErrorState message={getErrorMessage(postQuery.error)} onRetry={() => postQuery.refetch()} />
    );
  }

  const comments = commentsQuery.data ?? [];

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-canvas"
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 88 : 0}
    >
      <ScrollView
        className="flex-1"
        contentContainerClassName="px-4 pb-6"
        refreshControl={
          <RefreshControl
            refreshing={postQuery.isRefetching}
            onRefresh={() => {
              postQuery.refetch();
              commentsQuery.refetch();
            }}
            tintColor="#0077fa"
          />
        }
      >
        {/* 正文 */}
        <Text className="mt-4 text-2xl font-bold text-ink">{post.title}</Text>
        <Pressable
          className="mt-3 flex-row items-center gap-2 active:opacity-60"
          onPress={() => router.push(`/user/${post.authorId}`)}
          accessibilityRole="button"
          accessibilityLabel="查看作者主页"
        >
          <Avatar user={post.author} size={28} />
          <Text className="text-sm text-ink-muted">
            {post.author?.displayName || post.author?.username || '未知作者'}
          </Text>
          <Text className="text-xs text-ink-faint">· {formatDate(post.createdAt)}</Text>
        </Pressable>

        <View className="mt-4 flex-row gap-2">
          <ActionButton
            icon="heart-outline"
            activeIcon="heart"
            label={`点赞 ${formatCount(post.likeCount)}`}
            active={!!post.isLiked}
            onPress={() => like.mutate({ id: post.id, isLiked: !!post.isLiked })}
          />
          <ActionButton
            icon="star-outline"
            activeIcon="star"
            label={post.isFavorited ? '已收藏' : '收藏'}
            active={!!post.isFavorited}
            onPress={() => favorite.mutate({ id: post.id, isFavorited: !!post.isFavorited })}
          />
          {isOwnPost ? (
            <ActionButton
              icon="create-outline"
              activeIcon="create"
              label="编辑"
              active={false}
              onPress={() => router.push(`/posts/${post.id}/edit`)}
            />
          ) : null}
        </View>

        <View className="mt-5">
          <Markdown style={mdStyles}>{post.content}</Markdown>
        </View>

        {/* 评论 */}
        <View className="mt-8 gap-3">
          <Text className="text-base font-semibold text-ink">
            评论{comments.length > 0 ? `（${comments.length}）` : ''}
          </Text>

          {commentsQuery.isLoading ? (
            <Text className="text-sm text-ink-faint">评论加载中…</Text>
          ) : comments.length === 0 ? (
            <EmptyState title="还没有评论" subtitle="写下第一条评论吧" />
          ) : (
            <View className="gap-3">
              {comments.map((comment) => (
                <View
                  key={comment.id}
                  className="gap-1.5 rounded-lg border border-hairline bg-surface p-3"
                >
                  <View className="flex-row items-center gap-2">
                    <Avatar user={comment.author} size={20} />
                    <Text className="text-sm text-ink-muted">
                      {comment.author?.displayName || comment.author?.username || '匿名'}
                    </Text>
                    <Text className="text-xs text-ink-faint">{formatDate(comment.createdAt)}</Text>
                  </View>
                  <Text className="text-sm text-ink">{comment.content}</Text>
                </View>
              ))}
            </View>
          )}
        </View>

        {/* 相关阅读（recommendation-service） */}
        {(relatedQuery.data?.length ?? 0) > 0 ? (
          <View className="mt-8 gap-3">
            <Text className="text-base font-semibold text-ink">相关阅读</Text>
            <View className="gap-3">
              {(relatedQuery.data ?? []).map((item) => (
                <PostCard key={item.id} post={item} />
              ))}
            </View>
          </View>
        ) : null}
      </ScrollView>

      <CommentComposer postId={post.id} />
    </KeyboardAvoidingView>
  );
}
