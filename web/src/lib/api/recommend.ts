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
  likeCount: number;
  commentCount: number;
  score?: number;
  createdAt: string;
}

export interface TrendingPost {
  id: string;
  title: string;
  viewsCount: number;
  likeCount: number;
  commentCount: number;
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

export async function getFeed(params?: {
  page?: number;
  size?: number;
}): Promise<FeedRecommendation[]> {
  const response: ApiResponse<FeedRecommendation[]> = await client.get('/api/v1/recommend/feed', {
    params,
  });
  return response.data;
}

export async function getRelatedPosts(postId: string, topK = 10): Promise<FeedRecommendation[]> {
  const response: ApiResponse<FeedRecommendation[]> = await client.get(
    `/api/v1/recommend/related/${postId}`,
    {
      params: { topK },
    },
  );
  return response.data;
}

export async function getTrendingPosts(limit = 10): Promise<TrendingPost[]> {
  const response: ApiResponse<TrendingPost[]> = await client.get('/api/v1/recommend/trending', {
    params: { limit },
  });
  return response.data;
}

export async function getUserInterests(): Promise<UserInterestTag[]> {
  const response: ApiResponse<UserInterestTag[]> = await client.get('/api/v1/recommend/interests');
  return response.data;
}

export async function updateUserInterests(tags: string[]): Promise<UserInterestTag[]> {
  const response: ApiResponse<UserInterestTag[]> = await client.put(
    '/api/v1/recommend/interests',
    tags,
  );
  return response.data;
}

export async function sendFeedback(postId: string, action: string): Promise<void> {
  await client.post('/api/v1/recommend/feedback', { postId, action });
}
