import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from 'react-query';
import type { ReactNode } from 'react';
import { useToggleLike } from './usePosts';
import * as postsApi from '@/lib/api/posts';
import type { Post } from '@/types/post';

vi.mock('@/lib/api/posts');

const makePost = (overrides: Partial<Post> = {}): Post => ({
  id: '1',
  title: 'title',
  content: 'content',
  authorId: 'a1',
  author: { id: 'a1', username: 'u' },
  tags: [],
  status: 'published',
  likesCount: 5,
  commentsCount: 0,
  isLiked: false,
  isFavorited: false,
  createdAt: '2020-01-01',
  updatedAt: '2020-01-01',
  ...overrides,
});

const makeWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );

describe('useToggleLike', () => {
  it('rolls back the optimistic update when the request fails', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(['post', '1'], makePost({ id: '1', likesCount: 5, isLiked: false }));

    vi.mocked(postsApi.likePost).mockRejectedValue(new Error('boom'));

    const { result } = renderHook(() => useToggleLike(), { wrapper: makeWrapper(qc) });

    await act(async () => {
      await expect(result.current.mutateAsync({ id: '1', isLiked: false })).rejects.toThrow('boom');
    });

    const cached = qc.getQueryData<Post>(['post', '1']);
    expect(cached?.likesCount).toBe(5);
    expect(cached?.isLiked).toBe(false);
  });
});
