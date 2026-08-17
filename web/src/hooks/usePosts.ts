import {
  useQuery,
  useMutation,
  useQueryClient,
  keepPreviousData,
  QueryClient,
  QueryKey,
} from '@tanstack/react-query';
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
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      // 详情缓存若不删，gcTime 窗口内再进详情页会闪现已删除的文章
      queryClient.removeQueries({ queryKey: ['post', id] });
    },
  });
}

/** 承载博文列表的 query key 根（首页无限流 / 列表页 / 推荐流 / 搜索 / 个人主页） */
const POST_LIST_KEY_ROOTS: QueryKey[] = [['posts'], ['recommend'], ['search'], ['user']];

/**
 * 在所有缓存了博文列表的 query 中就地更新某篇博文，支持三种数据形态：
 * 裸 Post[]、分页 { items }、无限滚动 { pages: [{ items }] }。
 * 不含目标文章的缓存原样返回（保持引用不变，避免无谓重渲染）。
 */
const patchPostInLists = (queryClient: QueryClient, id: string, patch: (post: Post) => Post) => {
  for (const root of POST_LIST_KEY_ROOTS) {
    queryClient.setQueriesData({ queryKey: root }, (old: any) => {
      if (!old) return old;
      const mapItems = (items: any[]) => {
        if (!items.some((p) => p?.id === id)) return null;
        return items.map((p) => (p?.id === id ? patch(p) : p));
      };
      if (Array.isArray(old)) {
        const next = mapItems(old);
        return next ?? old;
      }
      if (Array.isArray(old.items)) {
        const next = mapItems(old.items);
        return next ? { ...old, items: next } : old;
      }
      if (Array.isArray(old.pages)) {
        let touched = false;
        const pages = old.pages.map((page: any) => {
          if (Array.isArray(page?.items)) {
            const next = mapItems(page.items);
            if (next) {
              touched = true;
              return { ...page, items: next };
            }
          }
          return page;
        });
        return touched ? { ...old, pages } : old;
      }
      return old;
    });
  }
};

/** 列表缓存失效（乐观更新失败后回滚用：直接让服务端真值覆盖） */
const invalidatePostLists = (queryClient: QueryClient) => {
  for (const root of POST_LIST_KEY_ROOTS) {
    queryClient.invalidateQueries({ queryKey: root });
  }
};

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
      // 列表缓存同步乐观更新，否则卡片上的红心/计数纹丝不动
      patchPostInLists(queryClient, id, (p) => ({
        ...p,
        isLiked: !isLiked,
        likeCount: (p.likeCount ?? 0) + (isLiked ? -1 : 1),
      }));

      return { previousPost };
    },
    onSuccess: (liked, { id }) => {
      // 用后端真实返回校正 isLiked（防乐观方向猜错）
      queryClient.setQueryData<Post>(['post', id], (old) =>
        old ? { ...old, isLiked: liked } : old,
      );
      patchPostInLists(queryClient, id, (p) => ({ ...p, isLiked: liked }));
    },
    onError: (_, __, context) => {
      if (context?.previousPost) {
        queryClient.setQueryData(['post', context.previousPost.id], context.previousPost);
      }
      invalidatePostLists(queryClient);
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
      patchPostInLists(queryClient, id, (p) => ({ ...p, isFavorited: !isFavorited }));

      return { previousPost };
    },
    onSuccess: (favorited, { id }) => {
      queryClient.setQueryData<Post>(['post', id], (old) =>
        old ? { ...old, isFavorited: favorited } : old,
      );
      patchPostInLists(queryClient, id, (p) => ({ ...p, isFavorited: favorited }));
    },
    onError: (_, __, context) => {
      if (context?.previousPost) {
        queryClient.setQueryData(['post', context.previousPost.id], context.previousPost);
      }
      invalidatePostLists(queryClient);
    },
  });
}
