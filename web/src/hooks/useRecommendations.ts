import { useQuery } from '@tanstack/react-query';
import { getFeed, getRelatedPosts, getTrendingPosts } from '@/lib/api/recommend';

/** 热门（走 recommendation-service，基于 blog_db 真实信号 × 时间衰减） */
export function useTrendingPosts(limit = 10) {
  return useQuery({
    queryKey: ['recommend', 'trending', limit],
    queryFn: () => getTrendingPosts(limit),
  });
}

/** 个性化推荐流（userId 由网关从 JWT 注入 X-User-Id，前端不传） */
export function useFeedRecommendations(params?: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ['recommend', 'feed', params],
    queryFn: () => getFeed(params),
  });
}

/** 相关博文（详情页用） */
export function useRelatedPosts(postId: string | undefined, topK = 5) {
  return useQuery({
    queryKey: ['recommend', 'related', postId, topK],
    queryFn: () => getRelatedPosts(postId!, topK),
    enabled: !!postId,
  });
}
