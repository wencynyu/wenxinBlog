import { useAuthStore } from './authStore';
import * as authApi from '@/lib/api/auth';
import * as SecureStore from 'expo-secure-store';

jest.mock('@/lib/api/auth');

const mockedAuthApi = jest.mocked(authApi);

const user = {
  id: 'u1',
  username: 'yuwenxin',
  email: 'yu@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const authResponse = {
  user,
  tokens: { accessToken: 'token-1', refreshToken: 'refresh-1', expiresIn: 3600 },
};

function resetState() {
  useAuthStore.setState({
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: true,
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);
  (SecureStore.setItemAsync as jest.Mock).mockClear();
  (SecureStore.deleteItemAsync as jest.Mock).mockClear();
  resetState();
});

describe('bootstrap（启动恢复会话）', () => {
  it('无 token：标记未认证并结束 loading', async () => {
    await useAuthStore.getState().bootstrap();

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().isLoading).toBe(false);
    expect(mockedAuthApi.getCurrentUser).not.toHaveBeenCalled();
  });

  it('有 token 且有效：恢复 user 并标记已认证', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('token-1');
    mockedAuthApi.getCurrentUser.mockResolvedValue(user);

    await useAuthStore.getState().bootstrap();

    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(useAuthStore.getState().user).toEqual(user);
    expect(useAuthStore.getState().token).toBe('token-1');
    expect(mockedAuthApi.getCurrentUser).toHaveBeenCalled();
  });

  it('token 已失效：清掉 SecureStore 与内存态', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('token-expired');
    mockedAuthApi.getCurrentUser.mockRejectedValue(new Error('401'));

    await useAuthStore.getState().bootstrap();

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('auth_token');
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().isLoading).toBe(false);
  });
});

describe('login（登录闭环）', () => {
  it('成功：建立会话并把 token 写入 SecureStore', async () => {
    mockedAuthApi.login.mockResolvedValue(authResponse);

    await useAuthStore.getState().login({ email: 'yu@example.com', password: 'pass' });

    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(useAuthStore.getState().user).toEqual(user);
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('auth_token', 'token-1');
  });

  it('失败：抛出错误且不建立会话', async () => {
    mockedAuthApi.login.mockRejectedValue({ code: 401, message: '邮箱或密码错误' });

    await expect(
      useAuthStore.getState().login({ email: 'yu@example.com', password: 'bad' }),
    ).rejects.toEqual({ code: 401, message: '邮箱或密码错误' });

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
  });
});

describe('register（注册后自动登录）', () => {
  it('注册成功后用同一凭据自动登录并落 token', async () => {
    mockedAuthApi.register.mockResolvedValue(authResponse);
    mockedAuthApi.login.mockResolvedValue(authResponse);

    await useAuthStore
      .getState()
      .register({ username: 'yuwenxin', email: 'yu@example.com', password: 'pass' });

    expect(mockedAuthApi.register).toHaveBeenCalled();
    expect(mockedAuthApi.login).toHaveBeenCalledWith({ email: 'yu@example.com', password: 'pass' });
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('auth_token', 'token-1');
  });
});

describe('logout', () => {
  it('调用后端登出并清空本地状态与 SecureStore', async () => {
    useAuthStore.setState({ user, token: 'token-1', isAuthenticated: true });
    mockedAuthApi.logout.mockResolvedValue(undefined);

    await useAuthStore.getState().logout();

    expect(mockedAuthApi.logout).toHaveBeenCalled();
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('auth_token');
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('后端登出失败也不阻塞本地登出', async () => {
    useAuthStore.setState({ user, token: 'token-1', isAuthenticated: true });
    mockedAuthApi.logout.mockRejectedValue(new Error('network'));

    await useAuthStore.getState().logout();

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(SecureStore.deleteItemAsync).toHaveBeenCalled();
  });
});
