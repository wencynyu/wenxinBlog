import client from './client';
import type { ApiResponse, PaginatedResponse } from '@/types/common';
import type {
  Role,
  Permission,
  CreateRoleRequest,
  CreatePermissionRequest,
  RoleDetail,
} from '@/types/permission';
import type { AdminUser, AdminUserDetail } from '@/types/admin';

// --- 权限 ---

export async function getPermissions(): Promise<Permission[]> {
  const r: ApiResponse<Permission[]> = await client.get('/api/v1/admin/permissions');
  return r.data;
}

export async function createPermission(data: CreatePermissionRequest): Promise<Permission> {
  const r: ApiResponse<Permission> = await client.post('/api/v1/admin/permissions', data);
  return r.data;
}

export async function deletePermission(code: string): Promise<void> {
  await client.delete(`/api/v1/admin/permissions/${code}`);
}

// --- 角色 ---

export async function getRoles(): Promise<Role[]> {
  const r: ApiResponse<Role[]> = await client.get('/api/v1/admin/roles');
  return r.data;
}

export async function getRoleDetail(id: number): Promise<RoleDetail> {
  const r: ApiResponse<RoleDetail> = await client.get(`/api/v1/admin/roles/${id}`);
  return r.data;
}

export async function createRole(data: CreateRoleRequest): Promise<{ id: number }> {
  const r: ApiResponse<{ id: number }> = await client.post('/api/v1/admin/roles', data);
  return r.data;
}

export async function deleteRole(id: number): Promise<void> {
  await client.delete(`/api/v1/admin/roles/${id}`);
}

export async function grantRolePermissions(id: number, permissionCodes: string[]): Promise<void> {
  await client.post(`/api/v1/admin/roles/${id}/permissions`, { permissionCodes });
}

export async function revokeRolePermission(id: number, code: string): Promise<void> {
  await client.delete(`/api/v1/admin/roles/${id}/permissions/${code}`);
}

// --- 用户 ---

export async function getAdminUsers(params?: {
  page?: number;
  pageSize?: number;
  search?: string;
}): Promise<PaginatedResponse<AdminUser>> {
  const r: ApiResponse<PaginatedResponse<AdminUser>> = await client.get('/api/v1/admin/users', {
    params,
  });
  return r.data;
}

export async function getAdminUserDetail(id: string): Promise<AdminUserDetail> {
  const r: ApiResponse<AdminUserDetail> = await client.get(`/api/v1/admin/users/${id}`);
  return r.data;
}

export async function banUser(id: string): Promise<void> {
  await client.post(`/api/v1/admin/users/${id}/ban`);
}

export async function unbanUser(id: string): Promise<void> {
  await client.post(`/api/v1/admin/users/${id}/unban`);
}

export async function assignRole(id: string, role: string): Promise<void> {
  await client.post(`/api/v1/admin/users/${id}/roles`, { role });
}
