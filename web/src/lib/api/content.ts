import client from './client';
import type { ApiResponse } from '@/types/common';

export interface UploadResponse {
  id: string;
  objectKey: string;
  cdnUrl: string;
  status: string;
  createdAt: string;
}

export interface MediaAsset {
  id: string;
  objectKey: string;
  originalName: string;
  mimeType: string;
  size: number;
  cdnUrl: string;
  status: string;
  postId?: string;
  createdAt: string;
}

export async function uploadFile(file: File): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append('file', file);

  // X-User-Id 由网关 AuthenticationFilter 从 JWT 注入到下游，客户端无需（也不应）设置；
  // Content-Type 由 axios 按 FormData 自动带上 boundary，手动设置反而会丢失 boundary。
  const response: ApiResponse<UploadResponse> = await client.post(
    '/api/v1/content/upload',
    formData,
  );
  return response.data;
}

export async function getFile(id: string): Promise<MediaAsset> {
  const response: ApiResponse<MediaAsset> = await client.get(`/api/v1/content/${id}`);
  return response.data;
}

export async function deleteFile(id: string): Promise<void> {
  await client.delete(`/api/v1/content/${id}`);
}
