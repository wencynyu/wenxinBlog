import client from './client';
import type { ApiResponse, PaginatedResponse, PaginationParams } from '@/types/common';
import type { Post } from '@/types/post';

export interface SearchPostResult {
  id: string;
  title: string;
  summary?: string;
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
  createdAt: string;
}

export interface SearchUserResult {
  id: string;
  username: string;
  displayName?: string;
  avatar?: string;
  bio?: string;
  followersCount: number;
  postsCount: number;
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
  const response: ApiResponse<PaginatedResponse<SearchUserResult>> = await client.get(
    '/api/v1/search/users',
    {
      params: { q: query, ...params },
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
