'use client';

import Link from 'next/link';
import Image from 'next/image';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { IconLikeHeart, IconStar, IconStarStroked, IconComment } from '@douyinfe/semi-icons';
import { Avatar, Toast } from '@douyinfe/semi-ui';
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
    <Link href={`/posts/${post.id}`} className="group block">
      <article className="bg-surface rounded-xl shadow-card hover:shadow-card-hover transition-all duration-200 overflow-hidden">
        <div className="p-5">
          {/* 作者信息 */}
          <div className="flex items-center mb-3">
            <Avatar
              size="small"
              src={post.author?.avatar}
              alt={post.author?.displayName || post.author?.username}
            >
              {(post.author?.displayName || post.author?.username || 'U')[0]}
            </Avatar>
            <div className="ml-2 flex items-center gap-2">
              <span className="text-sm font-medium text-ink">
                {post.author?.displayName || post.author?.username}
              </span>
              <span className="text-ink-faint text-xs font-mono">
                {dayjs(post.createdAt).fromNow()}
              </span>
            </div>
          </div>

          {/* 标题和摘要 */}
          <h3 className="text-lg font-semibold text-ink mb-2 line-clamp-2 group-hover:text-primary-700 transition-colors">
            {post.title}
          </h3>

          {post.summary && (
            <p className="text-ink-muted text-sm mb-3 line-clamp-2">{post.summary}</p>
          )}

          {/* 封面图 */}
          {post.coverImage && (
            <div className="mb-3 rounded-xl overflow-hidden relative h-48">
              <Image
                src={post.coverImage}
                alt={post.title}
                fill
                sizes="(max-width: 768px) 100vw, 33vw"
                className="object-cover group-hover:scale-105 transition-transform duration-300"
              />
            </div>
          )}

          {/* 标签 —— 自定义等宽 chip，与设计语言统一 */}
          {post.tags && post.tags.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-3">
              {post.tags.slice(0, 3).map((tag) => (
                <span
                  key={tag}
                  className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono bg-primary-50 text-primary-700"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}

          {/* 操作栏 */}
          <div className="flex items-center text-ink-faint text-sm font-mono">
            <button
              onClick={handleLike}
              className={`flex items-center mr-4 hover:text-red-500 transition-colors ${post.isLiked ? 'text-red-500' : ''}`}
            >
              <IconLikeHeart
                size="small"
                className="mr-1"
                style={{ color: post.isLiked ? '#ef4444' : undefined }}
              />
              <span>{post.likeCount || 0}</span>
            </button>

            <button
              onClick={handleFavorite}
              className={`flex items-center mr-4 hover:text-accent-500 transition-colors ${post.isFavorited ? 'text-accent-500' : ''}`}
            >
              {post.isFavorited ? (
                <IconStar size="small" className="mr-1" style={{ color: '#e08600' }} />
              ) : (
                <IconStarStroked size="small" className="mr-1" />
              )}
              <span>收藏</span>
            </button>

            <div className="flex items-center mr-4">
              <IconComment size="small" className="mr-1" />
              <span>{post.commentCount || 0}</span>
            </div>
          </div>
        </div>
      </article>
    </Link>
  );
}
