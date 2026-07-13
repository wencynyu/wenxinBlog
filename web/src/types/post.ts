// 博文相关类型
export interface Post {
  id: string;
  title: string;
  content: string;
  summary?: string;
  coverImage?: string;
  authorId: string;
  author?: {
    id: string;
    username: string;
    displayName?: string;
    avatar?: string;
  };
  tags?: string[];
  status: 'draft' | 'published';
  likeCount: number;
  commentCount: number;
  viewCount?: number;
  isLiked?: boolean;
  isFavorited?: boolean;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

export interface CreatePostRequest {
  title: string;
  content: string;
  summary?: string;
  coverImage?: string;
  tags?: string[];
  status?: 'draft' | 'published';
}

export interface UpdatePostRequest {
  title?: string;
  content?: string;
  summary?: string;
  coverImage?: string;
  tags?: string[];
  status?: 'draft' | 'published';
}

export interface PostQueryParams {
  page?: number;
  pageSize?: number;
  tag?: string;
  authorId?: string;
  status?: 'draft' | 'published';
  sortBy?: 'createdAt' | 'updatedAt' | 'likeCount' | 'commentCount';
  sortOrder?: 'asc' | 'desc';
}
