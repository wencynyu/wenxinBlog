import client from './client';
import { searchPosts, searchUsersApi, getTrendingTags } from './search';

jest.mock('./client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedGet = client.get as jest.Mock;

afterEach(() => {
  jest.clearAllMocks();
});

describe('search API 信封解包与参数', () => {
  it('searchPosts 透传 q 并解包分页数据', async () => {
    const paginated = { items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 };
    mockedGet.mockResolvedValue({ code: 0, message: 'ok', data: paginated });

    await expect(searchPosts('云原生', { page: 1, pageSize: 10 })).resolves.toEqual(paginated);
    expect(mockedGet).toHaveBeenCalledWith('/api/v1/search/blog', {
      params: { q: '云原生', page: 1, pageSize: 10 },
    });
  });

  it('searchUsersApi 把 pageSize 映射为 size（后端只认 size）', async () => {
    const paginated = { items: [], total: 0, page: 0, pageSize: 10, totalPages: 0 };
    mockedGet.mockResolvedValue({ code: 0, message: 'ok', data: paginated });

    await expect(searchUsersApi('dave', { page: 0, pageSize: 10 })).resolves.toEqual(paginated);
    expect(mockedGet).toHaveBeenCalledWith('/api/v1/search/users', {
      params: { q: 'dave', page: 0, size: 10 },
    });
  });

  it('getTrendingTags 解包字符串数组', async () => {
    mockedGet.mockResolvedValue({ code: 0, message: 'ok', data: ['云原生', 'K8s'] });

    await expect(getTrendingTags(5)).resolves.toEqual(['云原生', 'K8s']);
    expect(mockedGet).toHaveBeenCalledWith('/api/v1/search/trending/tags', {
      params: { limit: 5 },
    });
  });
});
