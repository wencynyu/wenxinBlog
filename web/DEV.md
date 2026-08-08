# Web Frontend

Web前端架构文档 - Next.js 14 + Semi-Design

> ⚠️ 本文档为早期规划，与当前实现有较大出入（如 React Query 实为 **v5**（非 v3.39）、无 (blog)/(user) 路由组与 `app/api/` BFF、无 sitemap/SSG、store 仅 authStore/uiStore）。**权威现状见 `docs/frontend/web.md`**。

## 技术栈

| 技术         | 版本 | 用途                   |
| ------------ | ---- | ---------------------- |
| Next.js      | 14.2 | React框架 (App Router) |
| React        | 18.3 | UI库                   |
| Semi-Design  | 2.65 | UI组件库               |
| Zustand      | 5.0  | 状态管理               |
| React Query  | 3.39 | 数据请求/缓存          |
| Axios        | 1.7  | HTTP客户端             |
| Tailwind CSS | 3.4  | CSS框架                |
| Day.js       | 1.11 | 日期处理               |
| Marked       | 12.0 | Markdown渲染           |
| Highlight.js | 11.9 | 代码高亮               |

## 项目结构

```
src/
├── app/                    # App Router页面
│   ├── (main)/            # 主布局 (Header/Navbar)
│   │   ├── layout.tsx     # 主布局组件
│   │   ├── page.tsx       # 首页
│   │   ├── feed/          # 推荐流
│   │   ├── trending/      # 热门
│   │   └── notifications/ # 通知
│   │
│   ├── (auth)/            # 认证相关 (无Header)
│   │   ├── layout.tsx
│   │   ├── login/         # 登录
│   │   ├── register/      # 注册
│   │   └── oauth/         # OAuth回调
│   │
│   ├── (blog)/            # 博文相关
│   │   ├── [id]/          # 博文详情
│   │   └── new/           # 发布博文
│   │
│   ├── (user)/            # 用户相关
│   │   ├── [id]/          # 用户主页
│   │   ├── [id]/posts     # 用户博文
│   │   └── settings/      # 个人设置
│   │
│   ├── search/            # 搜索页
│   └── api/               # API路由 (可选BFF)
│       └── proxy/
│
├── components/             # 组件
│   ├── layout/            # 布局组件
│   │   ├── Header.tsx
│   │   ├── Sidebar.tsx
│   │   ├── Footer.tsx
│   │   └── Navigation.tsx
│   │
│   ├── blog/              # 博文组件
│   │   ├── PostCard.tsx
│   │   ├── PostList.tsx
│   │   ├── PostDetail.tsx
│   │   ├── PostEditor.tsx
│   │   ├── CommentList.tsx
│   │   └── TagList.tsx
│   │
│   ├── user/              # 用户组件
│   │   ├── UserCard.tsx
│   │   ├── UserList.tsx
│   │   ├── FollowButton.tsx
│   │   └── Avatar.tsx
│   │
│   ├── ui/                # 通用UI组件
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── Modal.tsx
│   │   ├── Toast.tsx
│   │   └── Loading.tsx
│   │
│   └── provider/          # Context Provider
│       ├── QueryProvider.tsx
│       ├── ThemeProvider.tsx
│       └── AuthProvider.tsx
│
├── lib/                   # 工具函数
│   ├── api/               # API调用
│   │   ├── client.ts      # Axios配置
│   │   ├── auth.ts
│   │   ├── posts.ts
│   │   ├── users.ts
│   │   └── search.ts
│   ├── hooks/             # 自定义Hooks
│   │   ├── useAuth.ts
│   │   ├── useInfinite.ts
│   │   └── useDebounce.ts
│   ├── utils/             # 工具函数
│   │   ├── format.ts      # 格式化
│   │   ├── validation.ts  # 验证
│   │   └── storage.ts     # LocalStorage
│   └── constants/         # 常量
│       └── endpoints.ts
│
├── store/                 # Zustand状态
│   ├── authStore.ts
│   ├── userStore.ts
│   └── uiStore.ts
│
├── types/                 # TypeScript类型
│   ├── auth.ts
│   ├── post.ts
│   ├── user.ts
│   └── common.ts
│
└── styles/                # 样式
    ├── globals.css        # 全局样式
    └── markdown.css       # Markdown样式
```

## 核心页面

### 首页 (/)

```tsx
// app/(main)/page.tsx
export default function HomePage() {
  return (
    <InfiniteFeed
      queryKey={['feed']}
      fetchFn={() => api.posts.getFeed()}
      renderItem={(post) => <PostCard post={post} />}
    />
  );
}
```

### 博文详情 (/blog/[id])

```tsx
// app/(blog)/[id]/page.tsx
export default function PostPage({ params }) {
  const { data: post } = useQuery({
    queryKey: ['post', params.id],
    queryFn: () => api.posts.get(params.id),
  });

  return (
    <div>
      <PostDetail post={post} />
      <CommentList postId={params.id} />
      <RelatedPosts postId={params.id} />
    </div>
  );
}
```

### 用户主页 (/user/[id])

```tsx
// app/(user)/[id]/page.tsx
export default function UserPage({ params }) {
  const [activeTab, setActiveTab] = useState('posts');

  return (
    <div>
      <UserProfile userId={params.id} />
      <Tabs activeKey={activeTab} onChange={setActiveTab}>
        <TabPane tab="博文" itemKey="posts">
          <UserPosts userId={params.id} />
        </TabPane>
        <TabPane tab="喜欢" itemKey="likes">
          <UserLikes userId={params.id} />
        </TabPane>
        <TabPane tab="关注" itemKey="following">
          <UserFollowing userId={params.id} />
        </TabPane>
      </Tabs>
    </div>
  );
}
```

## 状态管理

### AuthStore (Zustand)

```typescript
// store/authStore.ts
interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  setUser: (user: User) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,

  login: async (email, password) => {
    const { user, token } = await api.auth.login(email, password);
    set({ user, token, isAuthenticated: true });
    storage.set('token', token);
  },

  logout: () => {
    set({ user: null, token: null, isAuthenticated: false });
    storage.remove('token');
  },

  setUser: (user) => set({ user }),
}));
```

### UIStore

```typescript
// store/uiStore.ts
interface UIState {
  theme: 'light' | 'dark';
  sidebarOpen: boolean;
  notifications: Notification[];
  setTheme: (theme: string) => void;
  toggleSidebar: () => void;
  addNotification: (notification: Notification) => void;
}
```

## API层设计

### Axios配置

```typescript
// lib/api/client.ts
import axios from 'axios';
import { useAuthStore } from '@/store/authStore';

const client = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  timeout: 10000,
});

// 请求拦截器
client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器
client.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

export default client;
```

### API模块

```typescript
// lib/api/posts.ts
import client from './client';

export const posts = {
  // 获取推荐流
  getFeed: (params: PaginationParams) => client.get('/posts/feed', { params }),

  // 获取博文详情
  get: (id: string) => client.get(`/posts/${id}`),

  // 创建博文
  create: (data: CreatePostDto) => client.post('/posts', data),

  // 更新博文
  update: (id: string, data: UpdatePostDto) => client.put(`/posts/${id}`, data),

  // 删除博文
  delete: (id: string) => client.delete(`/posts/${id}`),

  // 点赞
  like: (id: string) => client.post(`/posts/${id}/like`),

  // 收藏
  favorite: (id: string) => client.post(`/posts/${id}/favorite`),
};
```

## 路由设计

### 页面路由表

| 路径               | 页面     | 布局 | 认证 |
| ------------------ | -------- | ---- | ---- |
| `/`                | 首页     | main | 否   |
| `/feed`            | 推荐流   | main | 是   |
| `/trending`        | 热门     | main | 否   |
| `/notifications`   | 通知     | main | 是   |
| `/login`           | 登录     | auth | 否   |
| `/register`        | 注册     | auth | 否   |
| `/blog/[id]`       | 博文详情 | blog | 否   |
| `/blog/new`        | 发布博文 | blog | 是   |
| `/blog/[id]/edit`  | 编辑博文 | blog | 是   |
| `/user/[id]`       | 用户主页 | user | 否   |
| `/user/[id]/posts` | 用户博文 | user | 否   |
| `/settings`        | 个人设置 | main | 是   |
| `/search`          | 搜索     | main | 否   |

## 无限滚动

```typescript
// lib/hooks/useInfinite.ts
export function useInfinite<T>(
  queryKey: string[],
  fetchFn: (page: number) => Promise<PaginatedResponse<T>>
) {
  return useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam = 1 }) => fetchFn(pageParam),
    getNextPageParam: (lastPage) => {
      if (lastPage.page < lastPage.totalPages) {
        return lastPage.page + 1
      }
      return undefined
    },
  })
}

// 使用
function PostFeed() {
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfinite(
    ['posts'],
    (page) => api.posts.getFeed({ page })
  )

  return (
    <InfiniteScroll
      loadMore={fetchNextPage}
      hasMore={hasNextPage}
    >
      {data?.pages.map((page) => (
        page.data.map((post) => <PostCard key={post.id} post={post} />)
      ))}
    </InfiniteScroll>
  )
}
```

## 性能优化

### 图片优化

```tsx
import Image from 'next/image';

<Image
  src={post.coverImage}
  alt={post.title}
  width={800}
  height={400}
  loading="lazy"
  placeholder="blur"
  blurDataURL="/placeholder.jpg"
/>;
```

### 代码分割

```tsx
// 动态导入重型组件
const RichTextEditor = dynamic(() => import('@/components/RichTextEditor'), {
  loading: () => <Loading />,
  ssr: false,
});

// 路由级代码分割 (Next.js自动)
```

### 缓存策略

```typescript
// React Query配置
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5分钟内数据视为新鲜
      cacheTime: 30 * 60 * 1000, // 缓存30分钟
      refetchOnWindowFocus: false, // 窗口聚焦时不自动刷新
      retry: 1,
    },
  },
});
```

## SEO优化

### Metadata API

```tsx
// app/(blog)/[id]/page.tsx
export async function generateMetadata({ params }): Promise<Metadata> {
  const post = await api.posts.get(params.id);

  return {
    title: `${post.title} - WenxinBlog`,
    description: post.summary,
    openGraph: {
      title: post.title,
      description: post.summary,
      images: [post.coverImage],
      type: 'article',
      publishedTime: post.publishedAt,
      authors: [post.author.username],
    },
    twitter: {
      card: 'summary_large_image',
      title: post.title,
      description: post.summary,
      images: [post.coverImage],
    },
  };
}
```

### 结构化数据

```tsx
// 博文结构化数据
const jsonLd = {
  '@context': 'https://schema.org',
  '@type': 'BlogPosting',
  headline: post.title,
  image: post.coverImage,
  datePublished: post.publishedAt,
  dateModified: post.updatedAt,
  author: {
    '@type': 'Person',
    name: post.author.displayName,
  },
}

<script type="application/ld+json">
  {JSON.stringify(jsonLd)}
</script>
```

### Sitemap

```tsx
// app/sitemap.ts
export default async function sitemap() {
  const posts = await api.posts.getAll();

  return [
    {
      url: 'https://wenxinblog.com',
      lastModified: new Date(),
    },
    ...posts.map((post) => ({
      url: `https://wenxinblog.com/blog/${post.id}`,
      lastModified: post.updatedAt,
    })),
  ];
}
```

## 环境配置

```bash
# .env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_OSS_URL=http://localhost:9000
NEXT_PUBLIC_GA_ID=G-XXXXXXXXXX
NEXT_PUBLIC_SENTRY_DSN=https://xxx@sentry.io/xxx
```

## 开发

```bash
cd web
npm install
npm run dev
```

访问 http://localhost:3000

## 构建

```bash
npm run build
npm start
```

## 部署

### Vercel (推荐)

```bash
vercel deploy
```

### 阿里云OSS + CDN

```bash
# 静态资源上传
npm run build:oss

# 配置CDN
cdn_domain: https://cdn.wenxinblog.com
```
