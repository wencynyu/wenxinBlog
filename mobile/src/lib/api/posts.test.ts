import client from './client';
import { getPosts, likePost, getPost } from './posts';

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
const mockedPost = client.post as jest.Mock;

afterEach(() => {
  jest.clearAllMocks();
});

describe('posts API 信封解包', () => {
  it('getPosts 解包 ApiResponse.data 并透传分页参数', async () => {
    const paginated = { items: [{ id: 'p1' }], total: 1, page: 1, pageSize: 10, totalPages: 1 };
    // client 响应拦截器已解一层 axios response，这里模拟拦截器返回的 ApiResponse 信封
    mockedGet.mockResolvedValue({ code: 0, message: 'ok', data: paginated });

    const result = await getPosts({ page: 2, pageSize: 5 });

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/posts', { params: { page: 2, pageSize: 5 } });
    expect(result).toEqual(paginated);
  });

  it('getPost 解包单篇数据', async () => {
    const post = { id: 'p1', title: 'hello' };
    mockedGet.mockResolvedValue({ code: 0, message: 'ok', data: post });

    await expect(getPost('p1')).resolves.toEqual(post);
    expect(mockedGet).toHaveBeenCalledWith('/api/v1/posts/p1');
  });

  it('likePost 返回 toggle 后的布尔状态', async () => {
    mockedPost.mockResolvedValue({ code: 0, message: 'ok', data: true });

    await expect(likePost('p1')).resolves.toBe(true);
    expect(mockedPost).toHaveBeenCalledWith('/api/v1/posts/p1/like');
  });
});
