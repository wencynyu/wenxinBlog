import { Pressable, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Avatar } from '@/components/Avatar';
import { formatCount, formatDate } from '@/lib/format';

/** PostCard 实际消费的字段子集：Post / SearchPostResult / FeedRecommendation 均可传入 */
export interface PostCardData {
  id: string;
  title: string;
  summary?: string;
  coverImage?: string;
  author?: {
    id: string;
    username: string;
    displayName?: string;
    avatar?: string;
  };
  tags?: string[];
  likeCount: number;
  commentCount: number;
  viewCount?: number;
  createdAt: string;
}

function Meta({ icon, count }: { icon: keyof typeof Ionicons.glyphMap; count?: number }) {
  if (count === undefined) return null;
  return (
    <View className="flex-row items-center gap-1">
      <Ionicons name={icon} size={13} color="#8a919c" />
      <Text className="text-xs text-ink-faint">{formatCount(count)}</Text>
    </View>
  );
}

/**
 * 卡片列表统一间距（12px）：FlashList v2 用 ItemSeparatorComponent，
 * 普通 View 列表用 gap-3，两者保持一致。
 */
export function PostCardGap() {
  return <View className="h-3" />;
}

export function PostCard({ post }: { post: PostCardData }) {
  const router = useRouter();
  const tags = post.tags?.slice(0, 3) ?? [];

  return (
    <Pressable
      onPress={() => router.push(`/posts/${post.id}`)}
      className="flex-row gap-3.5 rounded-2xl border border-hairline bg-surface p-4 shadow-sm active:opacity-70"
      accessibilityRole="button"
      accessibilityLabel={`博文：${post.title}`}
    >
      <View className="flex-1 gap-1.5">
        <Text className="text-[17px] font-semibold leading-snug text-ink" numberOfLines={2}>
          {post.title}
        </Text>
        {post.summary ? (
          <Text className="text-sm leading-relaxed text-ink-muted" numberOfLines={2}>
            {post.summary}
          </Text>
        ) : null}

        {tags.length > 0 ? (
          <View className="flex-row flex-wrap gap-1.5 pt-0.5">
            {tags.map((tag) => (
              <Text
                key={tag}
                className="rounded-md bg-primary-50 px-1.5 py-0.5 text-[11px] text-primary-700 dark:bg-primary-900/40 dark:text-primary-200"
              >
                {tag}
              </Text>
            ))}
          </View>
        ) : null}

        <View className="flex-row items-center gap-2 pt-1.5">
          <Avatar user={post.author} size={20} />
          <Text className="flex-shrink text-xs text-ink-faint" numberOfLines={1}>
            {post.author?.displayName || post.author?.username || '未知作者'}
          </Text>
          <View className="flex-1" />
          <Meta icon="heart" count={post.likeCount} />
          <Meta icon="chatbubble-ellipses" count={post.commentCount} />
          <Meta icon="eye" count={post.viewCount} />
          <Text className="text-xs text-ink-faint">{formatDate(post.createdAt)}</Text>
        </View>
      </View>

      {post.coverImage ? (
        <Image
          source={{ uri: post.coverImage }}
          style={{ width: 92, height: 92, borderRadius: 12 }}
          contentFit="cover"
          transition={120}
          accessibilityLabel={`${post.title} 的封面图`}
        />
      ) : null}
    </Pressable>
  );
}
