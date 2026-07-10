import { useQuery, useMutation, useQueryClient } from 'react-query';
import {
  searchPosts,
  searchUsersApi,
  getSuggestions,
  getTrendingSearches,
  getTrendingTags,
  getSearchHistory,
  clearSearchHistory,
} from '@/lib/api/search';
import type { PaginationParams } from '@/types/common';

export function useSearchPosts(query: string, params?: PaginationParams) {
  return useQuery(
    ['search', 'posts', query, params],
    () => searchPosts(query, params),
    { enabled: !!query && query.length > 0, keepPreviousData: true }
  );
}

export function useSearchUsers(query: string, params?: PaginationParams) {
  return useQuery(
    ['search', 'users', query, params],
    () => searchUsersApi(query, params),
    { enabled: !!query && query.length > 0, keepPreviousData: true }
  );
}

export function useSuggestions(query: string) {
  return useQuery(
    ['suggest', query],
    () => getSuggestions(query),
    { enabled: !!query && query.length > 0 }
  );
}

export function useTrendingSearches() {
  return useQuery(['trending', 'searches'], () => getTrendingSearches(), {
    staleTime: 10 * 60 * 1000,
  });
}

export function useTrendingTags() {
  return useQuery(['trending', 'tags'], () => getTrendingTags(), {
    staleTime: 10 * 60 * 1000,
  });
}

export function useSearchHistory() {
  return useQuery(['search', 'history'], () => getSearchHistory(), {
    staleTime: 2 * 60 * 1000,
  });
}

export function useClearSearchHistory() {
  const queryClient = useQueryClient();

  return useMutation(clearSearchHistory, {
    onSuccess: () => {
      queryClient.invalidateQueries(['search', 'history']);
    },
  });
}
