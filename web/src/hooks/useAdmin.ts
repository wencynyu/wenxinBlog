import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '@/lib/api/admin';
import type { CreateRoleRequest, CreatePermissionRequest } from '@/types/permission';

// --- 权限 ---

export function usePermissions() {
  return useQuery({ queryKey: ['admin', 'permissions'], queryFn: adminApi.getPermissions });
}

export function useCreatePermission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreatePermissionRequest) => adminApi.createPermission(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'permissions'] }),
  });
}

export function useDeletePermission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => adminApi.deletePermission(code),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'permissions'] }),
  });
}

// --- 角色 ---

export function useRoles() {
  return useQuery({ queryKey: ['admin', 'roles'], queryFn: adminApi.getRoles });
}

export function useRoleDetail(id: number | null) {
  return useQuery({
    queryKey: ['admin', 'role', id],
    queryFn: () => adminApi.getRoleDetail(id!),
    enabled: id != null,
  });
}

export function useCreateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateRoleRequest) => adminApi.createRole(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'roles'] }),
  });
}

export function useDeleteRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminApi.deleteRole(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'roles'] }),
  });
}

export function useGrantPermissions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, permissionCodes }: { id: number; permissionCodes: string[] }) =>
      adminApi.grantRolePermissions(id, permissionCodes),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['admin', 'roles'] });
      qc.invalidateQueries({ queryKey: ['admin', 'role', variables.id] });
    },
  });
}

// --- 用户 ---

export function useAdminUsers(params?: { page?: number; pageSize?: number; search?: string }) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => adminApi.getAdminUsers(params),
  });
}

export function useBanUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.banUser(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });
}

export function useUnbanUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminApi.unbanUser(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });
}

export function useAssignRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => adminApi.assignRole(id, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });
}
