import client from './client';
import type { ApiResponse, PaginatedResponse, PaginationParams } from '@/types/common';
import type { Post } from '@/types/post';

export interface SearchPostResult {
  id: string;
  title: string;
  summary?: string;
  author: {
    id: string;
    username: string;
    displayName?: string;
    avatar?: string;
  };
  tags: string[];
  viewCount: number;
  likeCount: number;
  commentCount: number;
  createdAt: string;
}

export interface SearchUserResult {
  id: string;
  username: string;
  displayName?: string;
  avatarUrl?: string;
  bio?: string;
  followerCount: number;
  postCount: number;
}

export interface SuggestResponse {
  text: string;
  type: string;
}

export async function searchPosts(
  query: string,
  params?: PaginationParams & { tags?: string[]; authorId?: string },
): Promise<PaginatedResponse<SearchPostResult>> {
  const response: ApiResponse<PaginatedResponse<SearchPostResult>> = await client.get(
    '/api/v1/search/blog',
    {
      params: { q: query, ...params },
    },
  );
  return response.data;
}

export async function searchUsersApi(
  query: string,
  params?: PaginationParams,
): Promise<PaginatedResponse<SearchUserResult>> {
  // 后端 /search/users 只认 size（非 pageSize）；page 是 0-based。
  const { pageSize, ...rest } = params ?? {};
  const response: ApiResponse<PaginatedResponse<SearchUserResult>> = await client.get(
    '/api/v1/search/users',
    {
      params: { q: query, ...rest, ...(pageSize != null ? { size: pageSize } : {}) },
    },
  );
  return response.data;
}

export async function getSuggestions(query: string, type = 'blog'): Promise<SuggestResponse[]> {
  const response: ApiResponse<SuggestResponse[]> = await client.get('/api/v1/search/suggest', {
    params: { q: query, type },
  });
  return response.data;
}

export async function getTrendingSearches(limit = 10): Promise<string[]> {
  const response: ApiResponse<string[]> = await client.get('/api/v1/search/trending', {
    params: { limit },
  });
  return response.data;
}

export async function getTrendingTags(limit = 20): Promise<string[]> {
  const response: ApiResponse<string[]> = await client.get('/api/v1/search/trending/tags', {
    params: { limit },
  });
  return response.data;
}

export async function getSearchHistory(limit = 20): Promise<string[]> {
  const response: ApiResponse<string[]> = await client.get('/api/v1/search/history', {
    params: { limit },
  });
  return response.data;
}

export async function clearSearchHistory(): Promise<void> {
  await client.delete('/api/v1/search/history');
}
