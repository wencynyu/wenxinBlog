export interface Role {
  id: number;
  code: string;
  name: string;
  description: string;
  parentId: number | null;
  level: number;
  isSystem: boolean;
}

export interface Permission {
  id: number;
  code: string;
  name: string;
  resource: string;
  action: string;
  scope: string | null;
  description: string;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string;
  parentCode?: string;
}

export interface CreatePermissionRequest {
  code: string;
  name: string;
  resource: string;
  action: string;
  scope?: string;
  description?: string;
}

export interface RoleDetail {
  role: Role;
  permissions: Permission[];
}
