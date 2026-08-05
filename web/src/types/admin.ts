export interface AdminUser {
  id: string;
  username: string;
  email: string;
  avatarUrl?: string;
  status: string;
  createdAt: string;
  roles: string[];
}

export interface AdminUserDetail {
  user: AdminUser;
  permissions: string[];
}
