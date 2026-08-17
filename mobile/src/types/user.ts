// 用户相关类型（与 web 端 src/types/user.ts 保持一致）
export interface UserProfile {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatar?: string;
  bio?: string;
  location?: string;
  website?: string;
  followersCount: number;
  followingCount: number;
  postsCount: number;
  isFollowing: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  displayName?: string;
  bio?: string;
  avatar?: string;
  location?: string;
  website?: string;
}

export interface FollowUser {
  id: string;
  username: string;
  displayName?: string;
  avatar?: string;
  bio?: string;
  isFollowing: boolean;
  followersCount: number;
}
