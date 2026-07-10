export interface User {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatarUrl?: string;
  bio?: string;
  followerCount: number;
  followingCount: number;
  postCount: number;
}

export interface Post {
  id: string;
  title: string;
  content: string;
  summary?: string;
  coverImage?: string;
  author: User;
  tags: string[];
  viewCount: number;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

export interface Comment {
  id: string;
  postId: string;
  author: User;
  content: string;
  likeCount: number;
  createdAt: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
