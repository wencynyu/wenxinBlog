import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import * as postsApi from '@/lib/api/posts';
import type { Post, PostQueryParams, CreatePostRequest, UpdatePostRequest } from '@/types/post';

export function usePosts(params?: PostQueryParams) {
  return useQuery({
    queryKey: ['posts', params],
    queryFn: () => postsApi.getPosts(params),
    placeholderData: keepPreviousData,
  });
}

export function usePost(id: string, initialData?: Post) {
  return useQuery({
    queryKey: ['post', id],
    queryFn: () => postsApi.getPost(id),
    enabled: !!id,
    initialData,
  });
}

export function useCreatePost() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreatePostRequest) => postsApi.createPost(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['posts'] });
    },
  });
}

export function useUpdatePost() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdatePostRequest }) =>
      postsApi.updatePost(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      queryClient.invalidateQueries({ queryKey: ['post', variables.id] });
    },
  });
}

export function useDeletePost() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => postsApi.deletePost(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['posts'] });
    },
  });
}

export function useToggleLike() {
  const queryClient = useQueryClient();

  return useMutation({
    // 后端 POST /like 是 toggle（切换），统一走 POST；用返回的新状态校正，避免本地缓存与后端不同步导致 toggle 切错方向
    mutationFn: ({ id }: { id: string; isLiked: boolean }) => postsApi.likePost(id),
    onMutate: async ({ id, isLiked }) => {
      await queryClient.cancelQueries({ queryKey: ['post', id] });

      const previousPost = queryClient.getQueryData<Post>(['post', id]);

      if (previousPost) {
        queryClient.setQueryData(['post', id], {
          ...previousPost,
          isLiked: !isLiked,
          likeCount: previousPost.likeCount + (isLiked ? -1 : 1),
        });
      }

      return { previousPost };
    },
    onSuccess: (liked, { id }) => {
      // 用后端真实返回校正 isLiked（防乐观方向猜错）
      queryClient.setQueryData<Post>(['post', id], (old) =>
        old ? { ...old, isLiked: liked } : old,
      );
    },
    onError: (_, __, context) => {
      if (context?.previousPost) {
        queryClient.setQueryData(['post', context.previousPost.id], context.previousPost);
      }
    },
  });
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();

  return useMutation({
    // 后端 POST /favorite 是 toggle（切换），统一走 POST；用返回的新状态校正
    mutationFn: ({ id }: { id: string; isFavorited: boolean }) => postsApi.favoritePost(id),
    onMutate: async ({ id, isFavorited }) => {
      await queryClient.cancelQueries({ queryKey: ['post', id] });

      const previousPost = queryClient.getQueryData<Post>(['post', id]);

      if (previousPost) {
        queryClient.setQueryData(['post', id], {
          ...previousPost,
          isFavorited: !isFavorited,
        });
      }

      return { previousPost };
    },
    onSuccess: (favorited, { id }) => {
      queryClient.setQueryData<Post>(['post', id], (old) =>
        old ? { ...old, isFavorited: favorited } : old,
      );
    },
    onError: (_, __, context) => {
      if (context?.previousPost) {
        queryClient.setQueryData(['post', context.previousPost.id], context.previousPost);
      }
    },
  });
}
