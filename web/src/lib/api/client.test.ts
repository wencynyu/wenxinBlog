import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import client from './client';

const mock = new MockAdapter(client);

// jsdom 的 location.href 赋值不可观测，换成可断言的 stub
const originalLocation = window.location;

beforeEach(() => {
  mock.reset();
  localStorage.clear();
  // @ts-expect-error test stub
  delete window.location;
  // @ts-expect-error test stub
  window.location = { href: '' };
});

afterEach(() => {
  window.location = originalLocation;
  vi.restoreAllMocks();
});

describe('api client', () => {
  it('unwraps the response body', async () => {
    mock.onGet('/ping').reply(200, { ok: true });
    await expect(client.get('/ping')).resolves.toEqual({ ok: true });
  });

  it('sends credentials so the httpOnly session cookie travels cross-origin', () => {
    expect(client.defaults.withCredentials).toBe(true);
  });

  it('silently refreshes once and replays the original request on 401', async () => {
    mock.onPost('/api/v1/auth/refresh').reply(200, { code: 200, message: 'token refreshed' });
    mock.onGet('/secret').replyOnce(401, { code: 401, message: 'unauthorized', data: null });
    mock.onGet('/secret').reply(200, { ok: true });

    await expect(client.get('/secret')).resolves.toEqual({ ok: true });
    expect(window.location.href).toBe('');
  });

  it('does not retry more than once when the replayed request still 401s', async () => {
    mock.onPost('/api/v1/auth/refresh').reply(200, { code: 200, message: 'token refreshed' });
    mock.onGet('/secret').reply(401, { code: 401, message: 'unauthorized', data: null });

    await expect(client.get('/secret')).rejects.toMatchObject({ code: 401 });
    // 两次 401 = 原请求 + 一次重放
    expect(mock.history.get.filter((r) => r.url === '/secret')).toHaveLength(2);
  });

  it('clears the persisted session and redirects to /login when refresh fails', async () => {
    localStorage.setItem('auth-storage', '{"state":{"user":{"id":"u1"}}}');
    mock.onPost('/api/v1/auth/refresh').reply(401, { code: 401, message: 'invalid refresh token' });
    mock.onGet('/secret').reply(401, { code: 401, message: 'unauthorized', data: null });

    await expect(client.get('/secret')).rejects.toMatchObject({ code: 401 });
    expect(localStorage.getItem('auth-storage')).toBeNull();
    expect(window.location.href).toBe('/login');
  });

  it('does not refresh or redirect for auth-exempt 401s (e.g. wrong password)', async () => {
    mock
      .onPost('/api/v1/auth/login')
      .reply(401, { code: 401, message: 'invalid email or password' });

    await expect(client.post('/api/v1/auth/login', {})).rejects.toMatchObject({
      message: 'invalid email or password',
    });
    expect(mock.history.post.filter((r) => r.url?.includes('/auth/refresh'))).toHaveLength(0);
    expect(window.location.href).toBe('');
  });

  it('normalizes a timeout to a 408 error', async () => {
    mock.onGet('/slow').timeout();
    await expect(client.get('/slow')).rejects.toMatchObject({ code: 408, message: '请求超时' });
  });

  it('normalizes a network error to a 500 error', async () => {
    mock.onGet('/dead').networkError();
    await expect(client.get('/dead')).rejects.toMatchObject({ code: 500, message: '网络错误' });
  });
});
