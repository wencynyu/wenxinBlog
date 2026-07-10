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

export function usePost(id: string) {
  return useQuery({
    queryKey: ['post', id],
    queryFn: () => postsApi.getPost(id),
    enabled: !!id,
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
    mutationFn: async ({ id, isLiked }: { id: string; isLiked: boolean }) => {
      if (isLiked) {
        await postsApi.unlikePost(id);
      } else {
        await postsApi.likePost(id);
      }
      return { id, isLiked };
    },
    onMutate: async ({ id, isLiked }) => {
      await queryClient.cancelQueries({ queryKey: ['post', id] });

      const previousPost = queryClient.getQueryData<Post>(['post', id]);

      if (previousPost) {
        queryClient.setQueryData(['post', id], {
          ...previousPost,
          isLiked: !isLiked,
          likesCount: previousPost.likesCount + (isLiked ? -1 : 1),
        });
      }

      return { previousPost };
    },
    onError: (_, __, context) => {
      if (context?.previousPost) {
        queryClient.setQueryData(['post', context.previousPost.id], context.previousPost);
      }
    },
    onSettled: (_, __, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['post', id] });
    },
  });
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, isFavorited }: { id: string; isFavorited: boolean }) => {
      if (isFavorited) {
        await postsApi.unfavoritePost(id);
      } else {
        await postsApi.favoritePost(id);
      }
      return { id, isFavorited };
    },
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
    onError: (_, __, context) => {
      if (context?.previousPost) {
        queryClient.setQueryData(['post', context.previousPost.id], context.previousPost);
      }
    },
    onSettled: (_, __, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['post', id] });
    },
  });
}
