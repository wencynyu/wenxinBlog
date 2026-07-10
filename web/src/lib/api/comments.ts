import client from './client';
import type { ApiResponse } from '@/types/common';

export interface Comment {
  id: string;
  postId: string;
  authorId: string;
  author: {
    id: string;
    username: string;
    displayName?: string;
    avatar?: string;
  };
  content: string;
  createdAt: string;
  updatedAt: string;
}

export async function getComments(postId: string): Promise<Comment[]> {
  const response: ApiResponse<Comment[]> = await client.get(`/api/v1/posts/${postId}/comments`);
  return response.data;
}

export async function createComment(postId: string, content: string): Promise<Comment> {
  const response: ApiResponse<Comment> = await client.post(`/api/v1/posts/${postId}/comments`, { content });
  return response.data;
}

export async function deleteComment(id: string): Promise<void> {
  await client.delete(`/api/v1/comments/${id}`);
}
