'use client';

import Link from 'next/link';
import Image from 'next/image';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { Card, Avatar, Button, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconLikeHeart, IconStar, IconStarStroked, IconComment } from '@douyinfe/semi-icons';
import { useAuthStore } from '@/store/authStore';
import { useToggleLike, useToggleFavorite } from '@/hooks/usePosts';
import type { Post } from '@/types/post';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

interface PostCardProps {
  post: Post;
}

export default function PostCard({ post }: PostCardProps) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const toggleLike = useToggleLike();
  const toggleFavorite = useToggleFavorite();

  const handleLike = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      Toast.warning('请先登录');
      return;
    }
    toggleLike.mutate({ id: post.id, isLiked: post.isLiked || false });
  };

  const handleFavorite = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      Toast.warning('请先登录');
      return;
    }
    toggleFavorite.mutate({ id: post.id, isFavorited: post.isFavorited || false });
  };

  return (
    <Link href={`/posts/${post.id}`} className="group block no-underline">
      <Card
        className="shadow-card hover:shadow-card-hover transition-shadow overflow-hidden"
        cover={
          post.coverImage ? (
            <div className="relative h-36 overflow-hidden">
              <Image
                src={post.coverImage}
                alt={post.title}
                fill
                sizes="(max-width: 768px) 100vw, 33vw"
                className="object-cover group-hover:scale-105 transition-transform duration-300"
              />
            </div>
          ) : undefined
        }
        bodyStyle={{ padding: 20 }}
      >
        {/* 作者信息 */}
        <div className="flex items-center gap-2 mb-3">
          <Avatar
            size="small"
            src={post.author?.avatar}
            alt={post.author?.displayName || post.author?.username}
          >
            {(post.author?.displayName || post.author?.username || 'U')[0]}
          </Avatar>
          <span className="text-sm font-medium text-ink">
            {post.author?.displayName || post.author?.username}
          </span>
          <span className="text-ink-faint text-xs font-mono">
            {dayjs(post.createdAt).fromNow()}
          </span>
        </div>

        {/* 标题和摘要 */}
        <Typography.Title
          heading={4}
          ellipsis={{ rows: 2 }}
          className="group-hover:text-primary-700 transition-colors"
          style={{ marginBottom: 8 }}
        >
          {post.title}
        </Typography.Title>

        {post.summary && (
          <Typography.Paragraph type="tertiary" ellipsis={{ rows: 2 }} style={{ marginBottom: 12 }}>
            {post.summary}
          </Typography.Paragraph>
        )}

        {/* 标签 */}
        {post.tags && post.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mb-3">
            {post.tags.slice(0, 3).map((tag) => (
              <Tag key={tag} size="small" color="blue">
                {tag}
              </Tag>
            ))}
          </div>
        )}

        {/* 操作栏 */}
        <div className="flex items-center text-ink-faint text-sm font-mono">
          <Button
            theme="borderless"
            size="small"
            icon={<IconLikeHeart style={{ color: post.isLiked ? '#ef4444' : undefined }} />}
            onClick={handleLike}
          >
            {post.likeCount || 0}
          </Button>
          <Button
            theme="borderless"
            size="small"
            icon={
              post.isFavorited ? <IconStar style={{ color: '#e08600' }} /> : <IconStarStroked />
            }
            onClick={handleFavorite}
          >
            收藏
          </Button>
          <span className="flex items-center gap-1 ml-2">
            <IconComment size="small" />
            {post.commentCount || 0}
          </span>
        </div>
      </Card>
    </Link>
  );
}
