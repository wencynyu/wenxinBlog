import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { serverGet } from '@/lib/api/server';
import type { UserProfile } from '@/types/user';
import type { Post } from '@/types/post';
import type { PaginatedResponse } from '@/types/common';
import UserProfileView from './UserProfileView';

export const dynamic = 'force-dynamic';

type Params = { params: { id: string } };

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const profile = await serverGet<UserProfile>(`/api/v1/users/${params.id}`).catch(() => null);
  if (!profile) return { title: '用户不存在 - WenxinBlog' };
  const name = profile.displayName || profile.username;
  return {
    title: `${name} - WenxinBlog`,
    description: profile.bio || `${name} 的个人主页`,
    openGraph: { title: name, description: profile.bio || `${name} 的个人主页`, type: 'profile' },
  };
}

export default async function UserProfilePage({ params }: Params) {
  const profile = await serverGet<UserProfile>(`/api/v1/users/${params.id}`).catch(() => null);
  if (!profile) notFound();

  const postsResp = await serverGet<PaginatedResponse<Post>>(
    `/api/v1/posts?authorId=${encodeURIComponent(params.id)}&page=1&pageSize=10&status=published&sortBy=createdAt&sortOrder=desc`,
  ).catch(() => ({ items: [] as Post[], total: 0, page: 1, pageSize: 10, totalPages: 0 }));

  return <UserProfileView userId={params.id} profile={profile} posts={postsResp.items} />;
}
