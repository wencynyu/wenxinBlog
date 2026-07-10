import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as usersApi from '@/lib/api/users';
import type { UpdateProfileRequest } from '@/types/user';

export function useUserProfile(id: string) {
  return useQuery({
    queryKey: ['user', id],
    queryFn: () => usersApi.getUserProfile(id),
    enabled: !!id,
  });
}

export function useUpdateProfile(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => usersApi.updateProfile(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user', id] });
    },
  });
}

export function useFollowUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => usersApi.followUser(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['user', id] });
      queryClient.invalidateQueries({ queryKey: ['followers', id] });
      queryClient.invalidateQueries({ queryKey: ['following', id] });
    },
  });
}

export function useUnfollowUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => usersApi.unfollowUser(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['user', id] });
      queryClient.invalidateQueries({ queryKey: ['followers', id] });
      queryClient.invalidateQueries({ queryKey: ['following', id] });
    },
  });
}

export function useFollowers(id: string, params?: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ['followers', id, params],
    queryFn: () => usersApi.getFollowers(id, params),
    enabled: !!id,
  });
}

export function useFollowing(id: string, params?: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ['following', id, params],
    queryFn: () => usersApi.getFollowing(id, params),
    enabled: !!id,
  });
}
