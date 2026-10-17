> 最近更新：2026-08-02（对照实际代码核对）

# Web Frontend

Web 前端架构文档 — Next.js 14（App Router）+ Semi-Design。

实际代码位于仓库根 `web/`。

## 技术栈

（来自 `web/package.json`）

| 技术                              | 版本            | 用途                             |
| --------------------------------- | --------------- | -------------------------------- |
| Next.js                           | ^14.2           | React 框架（App Router）         |
| React / ReactDOM                  | ^18.3           | UI 库                            |
| @douyinfe/semi-ui + semi-icons    | ^2.65           | UI 组件库（Semi-Design）         |
| @tanstack/react-query             | ^5.101          | 数据请求 / 缓存（**v5**，非 v3） |
| Zustand                           | ^5.0            | 客户端状态管理                   |
| Axios                             | ^1.7            | HTTP 客户端                      |
| Tailwind CSS                      | ^3.4            | 原子化 CSS                       |
| marked + dompurify + highlight.js | 12 / 3.1 / 11.9 | Markdown 渲染 + 消毒 + 代码高亮  |
| dayjs                             | ^1.11           | 日期处理                         |

测试：Vitest 4 + @testing-library/react（单元，`*.test.tsx`），Playwright（e2e，`web/e2e/`）。

> 可观测性：前端**未接入 OpenTelemetry**（浏览器侧无 OTel SDK），后端链路追踪不由前端发起。

## 项目结构

实际目录（`web/src/`）：

```
src/
├── app/
│   ├── layout.tsx              # 根布局：QueryProvider → AuthProvider → ThemeProvider
│   ├── page.tsx                # 首页（/）：无限滚动的博文流 + Hero
│   ├── (auth)/                 # 认证（无 Navbar）
│   │   ├── login/page.tsx
│   │   └── register/page.tsx
│   ├── (main)/                 # 主布局（Header + Navbar + Sidebar）
│   │   ├── feed/page.tsx       # 推荐
│   │   ├── trending/page.tsx   # 热门
│   │   ├── posts/page.tsx      # 博文列表
│   │   ├── posts/[id]/page.tsx # 博文详情
│   │   ├── editor/page.tsx     # 写博文
│   │   ├── editor/[id]/page.tsx# 编辑博文
│   │   ├── search/page.tsx     # 搜索
│   │   └── settings/page.tsx   # 个人设置
│   └── user/[id]/page.tsx      # 用户主页（位于 app 根，非路由组内）
│
├── components/
│   ├── layout/                 # Header / Navbar / MainLayout / Sidebar / Footer
│   ├── post/                   # PostCard / PostList / MarkdownRenderer
│   ├── comment/                # CommentList / CommentInput
│   ├── recommend/              # RecommendationCard
│   ├── provider/               # QueryProvider / AuthProvider / ThemeProvider
│   └── common/                 # EmptyState 等通用组件
│
├── hooks/                      # usePosts / useAuth / useUser / useSearch / useRecommendations
├── lib/
│   └── api/                    # client / server(SSR) / auth / posts / users / comments / search / recommend / content
├── store/                      # authStore / uiStore（Zustand）
├── types/                      # auth / post / user / common
└── styles/
    └── globals.css
```

> 说明：博文相关页面在 `(main)` 路由组下以 `posts` / `editor` 命名。**没有** `(blog)` / `(user)` 路由组，也没有 `app/api/` BFF 路由。

## 路由表（实际）

| 路径                 | 页面               | 认证 |
| -------------------- | ------------------ | ---- |
| `/`                  | 首页（最新博文流） | 否   |
| `/feed`              | 推荐流             | 否   |
| `/trending`          | 热门               | 否   |
| `/posts`             | 博文列表           | 否   |
| `/posts/[id]`        | 博文详情           | 否   |
| `/editor`            | 写博文             | 是   |
| `/editor/[id]`       | 编辑博文           | 是   |
| `/search`            | 搜索               | 否   |
| `/settings`          | 个人设置           | 是   |
| `/user/[id]`         | 用户主页           | 否   |
| `/login` `/register` | 登录 / 注册        | 否   |

顶部导航（`Navbar.tsx`）暴露 4 项：首页 / 推荐 / 热门 / 博文。搜索、设置、用户主页通过页面内入口进入。

## API 层

- 入口：`lib/api/client.ts`，Axios 实例。
- `baseURL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'` —— **统一走网关**（localhost:8080），前端不直连后端微服务。
- 会话：auth-service 签发的 httpOnly cookie（`access_token`/`refresh_token`），axios `withCredentials: true`；401 时先静默 POST `/auth/refresh` 续期并重放一次，失败才清会话跳 `/login`。角色（useIsAdmin）来自 `GET /auth/me`（authStore.user.roles），前端不持有 token。
- 响应拦截器自动解包 `response.data`（即 `ApiResponse<T>`），错误归一化为 `{ code, message, data }`。
- 端点前缀统一 `/api/v1/...`（如 `/api/v1/posts`、`/api/v1/recommend/feed`、`/api/v1/recommend/trending`）。

### 分页与无限滚动

`PaginatedResponse<T> = { items, total, page, pageSize, totalPages }`。

列表页用 React Query v5 的 `useInfiniteQuery`（`initialPageParam` + `getNextPageParam`），示例见 `app/page.tsx`：

```tsx
const { data, hasNextPage, isFetchingNextPage, fetchNextPage } = useInfiniteQuery({
  queryKey: ['posts', 'home-feed'],
  queryFn: ({ pageParam }) =>
    getPosts({
      page: pageParam,
      pageSize: 10,
      status: 'published',
      sortBy: 'createdAt',
      sortOrder: 'desc',
    }),
  initialPageParam: 1,
  getNextPageParam: (lastPage) =>
    lastPage.page < lastPage.totalPages ? lastPage.page + 1 : undefined,
});
```

## 状态管理（Zustand）

- `authStore`：`{ user, token, isAuthenticated, login, logout, setUser }`。
- `uiStore`：主题 / 侧边栏等 UI 状态。

## 个人设置页（/settings）

`app/(main)/settings/page.tsx`：表单更新个人资料（昵称 / 简介 / 头像 / 所在地 / 网站）+ 兴趣标签（`TagInput`，走推荐服务 `getUserInterests` / `updateUserInterests`）。

> 历史修复：后端用户资料接口返回的 `id` 是 `user_profiles` 表主键，真实用户 id 在 `user_id` 字段，且字段为 snake_case。`setUser` 时已显式映射（`id: up.user_id`、`display_name` / `avatar_url` / ...），避免把 profile 主键塞进 `user.id`，导致「我的主页」跳转到不存在的用户。

## 广告

`components/ad/AdSlot.tsx`：广告位组件，调 ad-service `POST /api/v1/ads/decision`（经网关，公开）取素材渲染，当前投放 house ads（站内推广）。要点：

- **两个投放点**：feed 页第 3 张卡后（登录推荐流 / 匿名 PostList 两分支）+ 侧边栏 popular posts 下方（`Sidebar.tsx`）。
- **每次决策请求即计费**：`hooks/useAds.ts` 配置 `staleTime: Infinity` + 不重试 + 不后台刷新，每挂载只取一次；卸载后 5 分钟缓存内复用，避免重复扣费。
- **匿名标识**：未登录时 `lib/anonId.ts` 生成 localStorage UUID 作为 userId（后端 userId=null 会绕过频控、点击去重退化为共享 IP）。
- **点击**：fire-and-forget 上报 `POST /api/v1/ads/click`（`.catch(() => {})`，后端 24h 去重）后跳转 landing_url；站内路径走 Next Link，外链新窗口。
- **降级**：空结果/请求失败渲染 null；图片加载失败降级纯文字卡片。广告永不影响页面布局。

## SEO

- 根 `app/layout.tsx` 导出 `metadata`（title / description / openGraph / twitter + `metadataBase` + title 模板 `%s | WenxinBlog`）。
- `next.config.js` 配置全局响应头（`X-Frame-Options: SAMEORIGIN`、`X-DNS-Prefetch-Control: on`）与图片域名白名单。
- 字体通过 `<link>` 运行时加载（display=swap），未用 `next/font`，以规避构建期拉取 Google Fonts 失败。
- `app/robots.ts`：屏蔽 `/admin` `/editor` `/settings` `/auth` `/feed` `/login` `/register`，声明 sitemap 地址。
- `app/sitemap.ts`：`force-dynamic` 按请求生成；分页拉 `GET /api/v1/posts`（pageSize=100，上限 50 页）输出文章 URL（lastmod=updatedAt），静态入口 /、/posts、/trending；网关不可用时降级为仅静态条目（CI 无后端也能 build）。
- 博文详情 `posts/[id]/page.tsx`：`generateMetadata`（title/canonical/og:article/twitter）+ JSON-LD `BlogPosting` 结构化数据；经 `serverGet` 服务端取数，`React cache()` 让 metadata 与 JSON-LD 共用一次请求；**失败静默降级**为站点默认 meta（正文本就是 CSR，不重演历史上网关 OOM 导致 SSR 404 的问题）。
- 站点绝对 URL 经 `NEXT_PUBLIC_SITE_URL` 注入（`src/lib/site.ts` 读取；next.config.js / docker-compose.override.yml / .env.example 均有配置），生产部署时替换为真实域名。

## 环境变量

```
NEXT_PUBLIC_API_URL=http://localhost:8080   # 网关地址
NEXT_PUBLIC_OSS_URL=                         # 对象存储 / CDN（留空走默认）
```

见仓库根 `.env.example`。

## 开发与构建

```bash
cd web
npm install
npm run dev        # http://localhost:3000
npm run build && npm start
npm test           # vitest
npm run test:e2e   # playwright
```
