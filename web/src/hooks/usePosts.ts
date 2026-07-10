import { useQuery, useMutation, useQueryClient } from 'react-query';
import * as postsApi from '@/lib/api/posts';
import type { Post, PostQueryParams, CreatePostRequest, UpdatePostRequest } from '@/types/post';

export function usePosts(params?: PostQueryParams) {
  return useQuery(
    ['posts', params],
    () => postsApi.getPosts(params),
    { keepPreviousData: true }
  );
}

export function usePost(id: string) {
  return useQuery(
    ['post', id],
    () => postsApi.getPost(id),
    { enabled: !!id }
  );
}

export function useCreatePost() {
  const queryClient = useQueryClient();

  return useMutation(
    (data: CreatePostRequest) => postsApi.createPost(data),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['posts']);
      },
    }
  );
}

export function useUpdatePost() {
  const queryClient = useQueryClient();

  return useMutation(
    ({ id, data }: { id: string; data: UpdatePostRequest }) => postsApi.updatePost(id, data),
    {
      onSuccess: (_, variables) => {
        queryClient.invalidateQueries(['posts']);
        queryClient.invalidateQueries(['post', variables.id]);
      },
    }
  );
}

export function useDeletePost() {
  const queryClient = useQueryClient();

  return useMutation(
    (id: string) => postsApi.deletePost(id),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['posts']);
      },
    }
  );
}

export function useToggleLike() {
  const queryClient = useQueryClient();

  return useMutation(
    async ({ id, isLiked }: { id: string; isLiked: boolean }) => {
      if (isLiked) {
        await postsApi.unlikePost(id);
      } else {
        await postsApi.likePost(id);
      }
      return { id, isLiked };
    },
    {
      onMutate: async ({ id, isLiked }) => {
        await queryClient.cancelQueries(['post', id]);

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
        queryClient.invalidateQueries(['post', id]);
      },
    }
  );
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();

  return useMutation(
    async ({ id, isFavorited }: { id: string; isFavorited: boolean }) => {
      if (isFavorited) {
        await postsApi.unfavoritePost(id);
      } else {
        await postsApi.favoritePost(id);
      }
      return { id, isFavorited };
    },
    {
      onMutate: async ({ id, isFavorited }) => {
        await queryClient.cancelQueries(['post', id]);

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
        queryClient.invalidateQueries(['post', id]);
      },
    }
  );
}
