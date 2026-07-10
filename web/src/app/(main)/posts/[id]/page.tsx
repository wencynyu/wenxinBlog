import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { serverGet } from '@/lib/api/server';
import type { Post } from '@/types/post';
import type { FeedRecommendation } from '@/lib/api/recommend';
import PostDetailView from './PostDetailView';

// 后端在请求时才可访问；不在构建期预取，避免构建失败。
export const dynamic = 'force-dynamic';

type Params = { params: { id: string } };

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const post = await serverGet<Post>(`/api/v1/posts/${params.id}`).catch(() => null);
  if (!post) return { title: '博文不存在 - WenxinBlog' };
  return {
    title: `${post.title} - WenxinBlog`,
    description: post.summary || post.title,
    openGraph: {
      title: post.title,
      description: post.summary || post.title,
      type: 'article',
      locale: 'zh_CN',
    },
  };
}

export default async function PostDetailPage({ params }: Params) {
  const post = await serverGet<Post>(`/api/v1/posts/${params.id}`).catch(() => null);
  if (!post) notFound();

  const related = await serverGet<FeedRecommendation[]>(
    `/api/v1/recommend/related/${params.id}?topK=5`,
  ).catch(() => []);

  return <PostDetailView postId={params.id} initialPost={post} initialRelated={related} />;
}
