import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';

interface User {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatarUrl?: string;
}

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (token: string, user: User) => Promise<void>;
  logout: () => Promise<void>;
  loadUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  login: async (token, user) => {
    await SecureStore.setItemAsync('auth_token', token);
    set({ user, isAuthenticated: true, isLoading: false });
  },
  logout: async () => {
    await SecureStore.deleteItemAsync('auth_token');
    set({ user: null, isAuthenticated: false, isLoading: false });
  },
  loadUser: async () => {
    const token = await SecureStore.getItemAsync('auth_token');
    if (token) {
      // 从token解析用户信息或调用API
      set({ isLoading: false });
    } else {
      set({ isLoading: false });
    }
  },
}));
