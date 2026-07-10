'use client';

import { useRouter } from 'next/navigation';
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
import dynamic from 'next/dynamic';
import Image from 'next/image';
import MainLayout from '@/components/layout/MainLayout';
import CommentInput from '@/components/comment/CommentInput';
import CommentList from '@/components/comment/CommentList';
import EmptyState from '@/components/common/EmptyState';
import { usePost, useToggleLike, useToggleFavorite } from '@/hooks/usePosts';
import { useAuthStore } from '@/store/authStore';
import type { Post } from '@/types/post';
import type { FeedRecommendation } from '@/lib/api/recommend';

// 正文渲染较重（marked + highlight.js），按需加载，不进首屏共享 chunk
const MarkdownRenderer = dynamic(() => import('@/components/post/MarkdownRenderer'), {
  ssr: false,
  loading: () => <p className="text-ink-muted text-sm">加载正文…</p>,
});

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Title, Text } = Typography;

interface PostDetailViewProps {
  postId: string;
  initialPost: Post;
  initialRelated: FeedRecommendation[];
}

/**
 * 客户端 island：博文由 Server Component 预取并以 initialPost 注入，
 * 同时通过 usePost(id, initialPost) 写入 React Query 缓存，
 * 使点赞/收藏的乐观更新继续作用于同一 ['post', id] 缓存项。
 */
export default function PostDetailView({
  postId,
  initialPost,
  initialRelated,
}: PostDetailViewProps) {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const toggleLike = useToggleLike();
  const toggleFavorite = useToggleFavorite();

  const { data: post } = usePost(postId, initialPost);
  const relatedPosts = initialRelated;

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
          className="flex items-center text-ink-muted hover:text-ink mb-6 text-sm"
        >
          <IconArrowLeft className="mr-1" />
          返回
        </button>

        <article>
          <h1 className="text-3xl font-bold text-ink dark:text-gray-100 mb-4">{post.title}</h1>

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
                  <Text strong className="text-ink dark:text-gray-100">
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
                  <Tag color="violet" size="large">
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
                {relatedPosts.map((rp) => (
                  <Link
                    key={rp.id}
                    href={`/posts/${rp.id}`}
                    className="block p-3 rounded-lg hover:bg-canvas transition-colors"
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
