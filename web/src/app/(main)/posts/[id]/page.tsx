import PostDetailView from './PostDetailView';

// 客户端组件：不依赖网关在 SSR 时可用，避免网关 OOM 导致 404。
// usePost/useToggleLike 等 hooks 在客户端按需 fetch，可重试/显示加载态。
export const dynamic = 'force-dynamic';

export default function PostDetailPage({ params }: { params: { id: string } }) {
  return <PostDetailView postId={params.id} />;
}
