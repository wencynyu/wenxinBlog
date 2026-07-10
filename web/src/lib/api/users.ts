import client from './client';
import type { UserProfile, UpdateProfileRequest, FollowUser } from '@/types/user';
import type { ApiResponse, PaginatedResponse, PaginationParams } from '@/types/common';

export async function getUserProfile(id: string): Promise<UserProfile> {
  const response: ApiResponse<UserProfile> = await client.get(`/api/v1/users/${id}`);
  return response.data;
}

export async function getUserStats(id: string) {
  const response = await client.get(`/api/v1/users/${id}/stats`);
  return (response as any).data;
}

export async function updateProfile(id: string, data: UpdateProfileRequest): Promise<UserProfile> {
  const response: ApiResponse<UserProfile> = await client.put(`/api/v1/users/${id}`, data);
  return response.data;
}

export async function getFollowers(id: string, params?: PaginationParams): Promise<PaginatedResponse<FollowUser>> {
  const response: ApiResponse<PaginatedResponse<FollowUser>> = await client.get(`/api/v1/users/${id}/followers`, { params });
  return response.data;
}

export async function getFollowing(id: string, params?: PaginationParams): Promise<PaginatedResponse<FollowUser>> {
  const response: ApiResponse<PaginatedResponse<FollowUser>> = await client.get(`/api/v1/users/${id}/following`, { params });
  return response.data;
}

export async function followUser(id: string): Promise<void> {
  await client.post(`/api/v1/users/${id}/follow`);
}

export async function unfollowUser(id: string): Promise<void> {
  await client.delete(`/api/v1/users/${id}/follow`);
}

export async function searchUsers(query: string, params?: PaginationParams): Promise<PaginatedResponse<FollowUser>> {
  const response: ApiResponse<PaginatedResponse<FollowUser>> = await client.get('/api/v1/users/search', {
    params: { q: query, ...params },
  });
  return response.data;
}
