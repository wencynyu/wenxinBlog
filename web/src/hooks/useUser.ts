import { useQuery, useMutation, useQueryClient } from 'react-query';
import * as usersApi from '@/lib/api/users';
import type { UserProfile, UpdateProfileRequest } from '@/types/user';

export function useUserProfile(id: string) {
  return useQuery(
    ['user', id],
    () => usersApi.getUserProfile(id),
    { enabled: !!id }
  );
}

export function useUpdateProfile(id: string) {
  const queryClient = useQueryClient();

  return useMutation(
    (data: UpdateProfileRequest) => usersApi.updateProfile(id, data),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['user', id]);
      },
    }
  );
}

export function useFollowUser() {
  const queryClient = useQueryClient();

  return useMutation(
    (id: string) => usersApi.followUser(id),
    {
      onSuccess: (_, id) => {
        queryClient.invalidateQueries(['user', id]);
        queryClient.invalidateQueries(['followers', id]);
        queryClient.invalidateQueries(['following', id]);
      },
    }
  );
}

export function useUnfollowUser() {
  const queryClient = useQueryClient();

  return useMutation(
    (id: string) => usersApi.unfollowUser(id),
    {
      onSuccess: (_, id) => {
        queryClient.invalidateQueries(['user', id]);
        queryClient.invalidateQueries(['followers', id]);
        queryClient.invalidateQueries(['following', id]);
      },
    }
  );
}

export function useFollowers(id: string, params?: { page?: number; size?: number }) {
  return useQuery(
    ['followers', id, params],
    () => usersApi.getFollowers(id, params),
    { enabled: !!id }
  );
}

export function useFollowing(id: string, params?: { page?: number; size?: number }) {
  return useQuery(
    ['following', id, params],
    () => usersApi.getFollowing(id, params),
    { enabled: !!id }
  );
}
