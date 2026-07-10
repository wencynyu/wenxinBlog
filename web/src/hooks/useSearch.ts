import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
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
  return useQuery({
    queryKey: ['search', 'posts', query, params],
    queryFn: () => searchPosts(query, params),
    enabled: !!query && query.length > 0,
    placeholderData: keepPreviousData,
  });
}

export function useSearchUsers(query: string, params?: PaginationParams) {
  return useQuery({
    queryKey: ['search', 'users', query, params],
    queryFn: () => searchUsersApi(query, params),
    enabled: !!query && query.length > 0,
    placeholderData: keepPreviousData,
  });
}

export function useSuggestions(query: string) {
  return useQuery({
    queryKey: ['suggest', query],
    queryFn: () => getSuggestions(query),
    enabled: !!query && query.length > 0,
  });
}

export function useTrendingSearches() {
  return useQuery({
    queryKey: ['trending', 'searches'],
    queryFn: () => getTrendingSearches(),
    staleTime: 10 * 60 * 1000,
  });
}

export function useTrendingTags() {
  return useQuery({
    queryKey: ['trending', 'tags'],
    queryFn: () => getTrendingTags(),
    staleTime: 10 * 60 * 1000,
  });
}

export function useSearchHistory() {
  return useQuery({
    queryKey: ['search', 'history'],
    queryFn: () => getSearchHistory(),
    staleTime: 2 * 60 * 1000,
  });
}

export function useClearSearchHistory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: clearSearchHistory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['search', 'history'] });
    },
  });
}
