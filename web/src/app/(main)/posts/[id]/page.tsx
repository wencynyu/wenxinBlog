'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { Avatar, Tag, Button, Toast, Typography, Divider } from '@douyinfe/semi-ui';
import {
  IconLikeHeart,
  IconStar,
  IconStarStroked,
  IconEdit,
  IconArrowLeft,
  IconShareStroked,
} from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import dynamic from 'next/dynamic';
import Image from 'next/image';
import CommentInput from '@/components/comment/CommentInput';

// 正文渲染较重（marked + highlight.js），按需加载，不进首屏共享 chunk
const MarkdownRenderer = dynamic(() => import('@/components/post/MarkdownRenderer'), {
  ssr: false,
  loading: () => <p className="text-ink-muted text-sm">加载正文…</p>,
});
import CommentList from '@/components/comment/CommentList';
import EmptyState from '@/components/common/EmptyState';
import { usePost, useToggleLike, useToggleFavorite } from '@/hooks/usePosts';
import { getRelatedPosts } from '@/lib/api/recommend';
import { useAuthStore } from '@/store/authStore';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Title, Text } = Typography;

export default function PostDetailPage() {
  const params = useParams();
  const router = useRouter();
  const postId = params.id as string;
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const toggleLike = useToggleLike();
  const toggleFavorite = useToggleFavorite();

  // Read the post via the shared React Query hook. This also makes the
  // optimistic updates from useToggleLike / useToggleFavorite take effect
  // (they write to the same ['post', id] cache entry).
  const { data: post, isLoading } = usePost(postId);
  const [relatedPosts, setRelatedPosts] = useState<any[]>([]);

  useEffect(() => {
    let cancelled = false;
    getRelatedPosts(postId, 5)
      .then((data) => {
        if (!cancelled && data && data.length > 0) setRelatedPosts(data);
        else if (!cancelled) setRelatedPosts([]);
      })
      .catch(() => {
        if (!cancelled) setRelatedPosts([]);
      });
    return () => {
      cancelled = true;
    };
  }, [postId]);

  const handleShare = async () => {
    try {
      if (navigator.share) {
        await navigator.share({ title: post?.title, url: window.location.href });
      } else {
        await navigator.clipboard.writeText(window.location.href);
        Toast.success('链接已复制');
      }
    } catch {}
  };

  if (isLoading) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto">
          <div className="animate-pulse">
            <div className="h-8 bg-gray-200 rounded w-16 mb-6" />
            <div className="h-8 bg-gray-200 rounded w-4/5 mb-4" />
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 bg-gray-200 rounded-full" />
              <div className="h-4 bg-gray-200 rounded w-24" />
            </div>
            <div className="space-y-3">
              <div className="h-4 bg-gray-100 rounded w-full" />
              <div className="h-4 bg-gray-100 rounded w-full" />
              <div className="h-4 bg-gray-100 rounded w-3/4" />
            </div>
          </div>
        </div>
      </MainLayout>
    );
  }

  if (!post) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto py-20">
          <EmptyState
            title="博文不存在或加载失败"
            description="该博文可能已被删除，或后端服务暂不可用"
            actionText="返回首页"
            onAction={() => router.push('/')}
          />
        </div>
      </MainLayout>
    );
  }

  const isAuthor = user && user.id === post.authorId;

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-3xl mx-auto">
        {/* 返回按钮 */}
        <button
          onClick={() => router.back()}
          className="flex items-center text-gray-500 hover:text-gray-700 mb-6 text-sm"
        >
          <IconArrowLeft className="mr-1" />
          返回
        </button>

        <article>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-4">{post.title}</h1>

          {/* 作者信息 */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center">
              <Link href={`/user/${post.authorId}`}>
                <Avatar
                  size="default"
                  src={post.author?.avatar}
                  alt={post.author?.displayName || post.author?.username}
                >
                  {(post.author?.displayName || post.author?.username || 'U')[0]}
                </Avatar>
              </Link>
              <div className="ml-3">
                <Link href={`/user/${post.authorId}`}>
                  <Text strong className="text-gray-900 dark:text-gray-100">
                    {post.author?.displayName || post.author?.username}
                  </Text>
                </Link>
                <br />
                <Text type="tertiary" size="small">
                  {dayjs(post.createdAt).format('YYYY-MM-DD HH:mm')}
                </Text>
              </div>
            </div>

            {isAuthor && (
              <div className="flex gap-2">
                <Link href={`/editor/${post.id}`}>
                  <Button icon={<IconEdit />} theme="borderless" size="small">
                    编辑
                  </Button>
                </Link>
              </div>
            )}
          </div>

          {/* 封面图 */}
          {post.coverImage && (
            <div className="mb-6 rounded-xl overflow-hidden relative h-[360px] bg-canvas">
              <Image
                src={post.coverImage}
                alt={post.title}
                fill
                priority
                sizes="(max-width: 768px) 100vw, 768px"
                className="object-cover"
              />
            </div>
          )}

          {/* 标签 */}
          {post.tags && post.tags.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-6">
              {post.tags.map((tag: string) => (
                <Link key={tag} href={`/posts?tag=${encodeURIComponent(tag)}`}>
                  <Tag color="cyan" size="large">
                    #{tag}
                  </Tag>
                </Link>
              ))}
            </div>
          )}

          {/* 正文 */}
          <MarkdownRenderer content={post.content} />

          {/* 操作栏 */}
          <Divider />
          <div className="flex items-center justify-center gap-6 py-4">
            <Button
              icon={<IconLikeHeart />}
              theme={post.isLiked ? 'solid' : 'borderless'}
              type={post.isLiked ? 'danger' : 'tertiary'}
              onClick={() => {
                if (!isAuthenticated) {
                  Toast.warning('请先登录');
                  return;
                }
                toggleLike.mutate({ id: post.id, isLiked: post.isLiked });
              }}
            >
              {post.likesCount || 0}
            </Button>

            <Button
              icon={post.isFavorited ? <IconStar /> : <IconStarStroked />}
              theme={post.isFavorited ? 'solid' : 'borderless'}
              type={post.isFavorited ? 'warning' : 'tertiary'}
              onClick={() => {
                if (!isAuthenticated) {
                  Toast.warning('请先登录');
                  return;
                }
                toggleFavorite.mutate({ id: post.id, isFavorited: post.isFavorited });
              }}
            >
              收藏
            </Button>

            <Button icon={<IconShareStroked />} theme="borderless" onClick={handleShare}>
              分享
            </Button>
          </div>

          {/* 评论区 */}
          <Divider />
          <div className="mb-8">
            <Title heading={4} className="mb-4">
              评论 ({post.commentsCount || 0})
            </Title>
            <CommentInput postId={postId} />
          </div>
          <CommentList postId={postId} />
        </article>

        {/* 相关推荐 */}
        {relatedPosts && relatedPosts.length > 0 && (
          <>
            <Divider />
            <div>
              <Title heading={4} className="mb-4">
                相关推荐
              </Title>
              <div className="space-y-3">
                {relatedPosts.map((rp: any) => (
                  <Link
                    key={rp.id}
                    href={`/posts/${rp.id}`}
                    className="block p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <Text strong className="line-clamp-1">
                      {rp.title}
                    </Text>
                    <div className="flex items-center gap-3 mt-1">
                      <Text type="tertiary" size="small">
                        {rp.author?.displayName || rp.author?.username}
                      </Text>
                      <Text type="tertiary" size="small">
                        {rp.likesCount} 赞
                      </Text>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </MainLayout>
  );
}
