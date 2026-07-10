import { describe, it, expect, beforeEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import client, { setToken } from './client';

const mock = new MockAdapter(client);

beforeEach(() => {
  mock.reset();
  localStorage.clear();
});

describe('api client', () => {
  it('unwraps the response body', async () => {
    mock.onGet('/ping').reply(200, { ok: true });
    await expect(client.get('/ping')).resolves.toEqual({ ok: true });
  });

  it('injects the Bearer token from localStorage', async () => {
    setToken('abc123');
    let captured: string | undefined;
    mock.onGet('/ping').reply((config) => {
      captured = config.headers?.Authorization as string | undefined;
      return [200, { ok: true }];
    });
    await client.get('/ping');
    expect(captured).toBe('Bearer abc123');
  });

  it('does not add Authorization when there is no token', async () => {
    let captured: string | undefined;
    mock.onGet('/ping').reply((config) => {
      captured = config.headers?.Authorization as string | undefined;
      return [200, { ok: true }];
    });
    await client.get('/ping');
    expect(captured).toBeUndefined();
  });

  it('clears the token and redirects on 401', async () => {
    setToken('leaked');
    mock.onGet('/secret').reply(401, { code: 401, message: 'unauthorized', data: null });
    await expect(client.get('/secret')).rejects.toMatchObject({ code: 401 });
    expect(localStorage.getItem('auth_token')).toBeNull();
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
