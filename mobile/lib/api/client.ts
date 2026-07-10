import * as SecureStore from 'expo-secure-store';

const API_URL = 'http://localhost:8080';

async function getToken(): Promise<string | null> {
  return SecureStore.getItemAsync('auth_token');
}

export async function apiClient<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (res.status === 401) {
    await SecureStore.deleteItemAsync('auth_token');
    throw new Error('Unauthorized');
  }
  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.message || `Request failed: ${res.status}`);
  }
  return res.json();
}
