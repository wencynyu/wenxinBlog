import client from './client';
import type { ApiResponse } from '@/types/common';
import type { Post } from '@/types/post';

export interface FeedRecommendation {
  id: string;
  title: string;
  summary?: string;
  coverImage?: string;
  authorId: string;
  author: {
    id: string;
    username: string;
    displayName?: string;
    avatar?: string;
  };
  tags: string[];
  likesCount: number;
  commentsCount: number;
  score?: number;
  createdAt: string;
}

export interface TrendingPost {
  id: string;
  title: string;
  viewsCount: number;
  likesCount: number;
  author: {
    id: string;
    username: string;
    displayName?: string;
    avatar?: string;
  };
  createdAt: string;
}

export interface UserInterestTag {
  tag: string;
  weight: number;
}

export async function getFeed(
  userId: string,
  params?: { page?: number; size?: number }
): Promise<FeedRecommendation[]> {
  const response: ApiResponse<FeedRecommendation[]> = await client.get('/api/v1/recommend/feed', {
    params: { userId, ...params },
  });
  return response.data;
}

export async function getRelatedPosts(postId: string, topK = 10): Promise<FeedRecommendation[]> {
  const response: ApiResponse<FeedRecommendation[]> = await client.get(`/api/v1/recommend/related/${postId}`, {
    params: { topK },
  });
  return response.data;
}

export async function getTrendingPosts(limit = 10): Promise<TrendingPost[]> {
  const response: ApiResponse<TrendingPost[]> = await client.get('/api/v1/recommend/trending', {
    params: { limit },
  });
  return response.data;
}

export async function getUserInterests(userId: string): Promise<UserInterestTag[]> {
  const response: ApiResponse<UserInterestTag[]> = await client.get('/api/v1/recommend/interests', {
    params: { userId },
  });
  return response.data;
}

export async function updateUserInterests(userId: string, tags: string[]): Promise<UserInterestTag[]> {
  const response: ApiResponse<UserInterestTag[]> = await client.put('/api/v1/recommend/interests', tags, {
    params: { userId },
  });
  return response.data;
}

export async function sendFeedback(userId: string, postId: string, action: string): Promise<void> {
  await client.post('/api/v1/recommend/feedback', { userId, postId, action });
}
