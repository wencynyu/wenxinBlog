import { cache } from 'react';
import type { Metadata } from 'next';
import PostDetailView from './PostDetailView';
import { serverGet } from '@/lib/api/server';
import { SITE_URL } from '@/lib/site';
import type { Post } from '@/types/post';

// 客户端组件：正文渲染不依赖网关在 SSR 时可用，避免网关 OOM 导致 404。
// SEO（title/OG/JSON-LD）由本文件的 generateMetadata/JSON-LD 以服务端单次请求提供，
// 失败时静默降级为站点默认 meta，正文仍由客户端 CSR 渲染（usePost 等 hooks 按需 fetch）。
export const dynamic = 'force-dynamic';

/** generateMetadata 与页面渲染共用一次请求（React cache 按请求去重）。 */
const getPost = cache(async (id: string): Promise<Post | null> => {
  try {
    return await serverGet<Post>(`/api/v1/posts/${id}`);
  } catch {
    return null;
  }
});

function descriptionOf(post: Post): string {
  const summary = post.summary?.trim();
  if (summary) return summary.slice(0, 200);
  // 无摘要时剥掉 Markdown 常见符号取正文前 160 字
  const plain = (post.content || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#*`>~\[\]()!|-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return plain.slice(0, 160);
}

export async function generateMetadata({ params }: { params: { id: string } }): Promise<Metadata> {
  const post = await getPost(params.id);
  if (!post) return {}; // 网关不可用/文章不存在 → 用根布局默认 meta
  const description = descriptionOf(post);
  const url = `${SITE_URL}/posts/${post.id}`;
  return {
    title: post.title,
    description,
    alternates: { canonical: url },
    openGraph: {
      title: post.title,
      description,
      url,
      type: 'article',
      locale: 'zh_CN',
      publishedTime: post.publishedAt || post.createdAt,
      modifiedTime: post.updatedAt,
      ...(post.coverImage ? { images: [post.coverImage] } : {}),
    },
    twitter: {
      card: post.coverImage ? 'summary_large_image' : 'summary',
      title: post.title,
      description,
      ...(post.coverImage ? { images: [post.coverImage] } : {}),
    },
  };
}

/** BlogPosting 结构化数据（Google 富结果）。 */
function jsonLdOf(post: Post): object {
  const url = `${SITE_URL}/posts/${post.id}`;
  return {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: post.title,
    description: descriptionOf(post),
    datePublished: post.publishedAt || post.createdAt,
    dateModified: post.updatedAt,
    ...(post.coverImage ? { image: [post.coverImage] } : {}),
    author: {
      '@type': 'Person',
      name: post.author?.displayName || post.author?.username || 'WenxinBlog 用户',
    },
    ...(post.tags?.length ? { keywords: post.tags.join(',') } : {}),
    mainEntityOfPage: { '@type': 'WebPage', '@id': url },
  };
}

export default async function PostDetailPage({ params }: { params: { id: string } }) {
  const post = await getPost(params.id);
  return (
    <>
      {post && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLdOf(post)) }}
        />
      )}
      <PostDetailView postId={params.id} />
    </>
  );
}
