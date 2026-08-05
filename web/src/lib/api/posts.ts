import client from './client';
import type { Post, CreatePostRequest, UpdatePostRequest, PostQueryParams } from '@/types/post';
import type { ApiResponse, PaginatedResponse } from '@/types/common';

/**
 * 获取博文列表
 */
export async function getPosts(params?: PostQueryParams): Promise<PaginatedResponse<Post>> {
  const response: ApiResponse<PaginatedResponse<Post>> = await client.get('/api/v1/posts', {
    params,
  });
  return response.data;
}

/**
 * 获取博文详情
 */
export async function getPost(id: string): Promise<Post> {
  const response: ApiResponse<Post> = await client.get(`/api/v1/posts/${id}`);
  return response.data;
}

/**
 * 创建博文
 */
export async function createPost(data: CreatePostRequest): Promise<Post> {
  const response: ApiResponse<Post> = await client.post('/api/v1/posts', data);
  return response.data;
}

/**
 * 更新博文
 */
export async function updatePost(id: string, data: UpdatePostRequest): Promise<Post> {
  const response: ApiResponse<Post> = await client.put(`/api/v1/posts/${id}`, data);
  return response.data;
}

/**
 * 删除博文
 */
export async function deletePost(id: string): Promise<void> {
  await client.delete(`/api/v1/posts/${id}`);
}

/**
 * 点赞博文（后端 POST /like 是 toggle：切换赞/取消，返回新状态 true=已赞 / false=已取消）
 */
export async function likePost(id: string): Promise<boolean> {
  const response: ApiResponse<boolean> = await client.post(`/api/v1/posts/${id}/like`);
  return response.data;
}

/**
 * 收藏博文（后端 POST /favorite 是 toggle：切换收藏/取消，返回新状态）
 */
export async function favoritePost(id: string): Promise<boolean> {
  const response: ApiResponse<boolean> = await client.post(`/api/v1/posts/${id}/favorite`);
  return response.data;
}
