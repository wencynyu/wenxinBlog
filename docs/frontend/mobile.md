> 最近更新：2026-08-18（SDK 57 重建后对照实际代码核对）

# Mobile（React Native + Expo）

> **当前状态：SDK 57 重建完成（阶段 0-2 + 工程化），核心闭环可运行。**
>
> 2026-08-18 用 Expo SDK 57 整体重建了旧 Expo 51 POC（旧代码见 git 历史，commit d590f9f）：
> 路由命名错误、依赖幻影、API 契约错配等结构性问题已一次性解决；逻辑层（types/api/hooks/store）
> 从 web 端移植，与后端 `/api/v1/*` 契约对齐。本文档据实记录：已实现标 ✅，占位页标 🔸，未实现标 📋。

## 已实现 ✅

### 基础设施

- **脚手架**：`create-expo-app`（blank-typescript）+ expo-router，目录规范 `app/`（路由）+ `src/`（api/hooks/store/types/components/constants），`@/*` → `src/*` 别名。
- **API client**：`src/lib/api/client.ts` —— axios，`EXPO_PUBLIC_API_URL` 环境变量（.env/.env.example），Bearer token 异步读 SecureStore，响应拦截器解包 `ApiResponse{code,message,data}` 信封，401 清 token + 回调通知 app 层跳登录。
- **业务模块**：auth / posts / comments / users（与 web 端 `web/src/lib/api/*` 同构，端点与信封一致）。
- **状态**：`src/store/authStore.ts`（zustand + persist）——token 存 SecureStore（Keychain），user 会话态存 AsyncStorage；`bootstrap()` 启动时用 `/auth/me` 验证 token 并恢复会话（比 web 端的"有 token 即可信"更严格）。
- **数据层**：TanStack Query hooks —— `useInfinitePosts`（totalPages 终止条件）、`usePost`、`useToggleLike/Favorite`（乐观更新+后端校正）、`useComments/useCreateComment`、`useUserProfile` 等。
- **主题**：NativeWind v4 + Tailwind 3，色板与 web 端 tailwind.config.js 一致（primary=Semi 蓝 #0077FA、accent 琥珀、ink/canvas/surface 语义色）；`dark:` 变体跟随系统深色模式。

### 页面

- **认证门**：`app/_layout.tsx` —— 启动 splash → bootstrap 验证会话 → 按 `isAuthenticated` 重定向；401 全局回调回登录页。
- **登录 / 注册**：`app/(auth)/{login,register}.tsx` —— 品牌视觉（琥珀"文"印章字标 + 巨型水印字）、表单校验、密码显隐、loading 态、错误提示；注册后自动登录；token 落 SecureStore。
- **首页 Feed**：`app/(tabs)/index.tsx` —— FlashList v2 无限分页 + 下拉刷新 + 加载/错误/空三态；头部品牌字标 + 搜索入口。
- **发现**：`app/(tabs)/discover.tsx` —— 热榜 Top10（recommendation-service）+ 个性化推荐流。
- **搜索**：`app/search.tsx` —— 300ms 防抖、热搜词/热门标签/搜索历史（服务端）、博文+用户混合结果。
- **博文详情**：`app/posts/[id]/index.tsx` —— Markdown 渲染、点赞/收藏（乐观更新）、评论列表 + 发评论、作者主页链接、相关阅读（推荐服务）、自己的博文显示编辑入口。
- **发博文 / 编辑**：`app/posts/new.tsx`、`app/posts/[id]/edit.tsx` —— 共用 `PostEditor`（标题/摘要/标签/Markdown 正文，发布/存草稿）；"发布" tab 直接进编辑器。
- **用户主页**：`app/user/[id].tsx` —— 资料 + 粉丝/关注/博文统计 + 关注/取关 + TA 的博文列表；自己的主页显示编辑资料入口。
- **设置**：`app/settings.tsx` —— 编辑资料（昵称/简介/所在地/网站，保存后回写 authStore）+ 关于。
- **我的**：`app/(tabs)/profile.tsx` —— 当前用户信息、user-service 实时统计、我的主页/编辑资料入口、退出登录。

### 占位页 🔸（有真实页面，功能待后续）

- `(tabs)/notifications`（通知，依赖后端通知能力，尚未规划 API）。

### 工程化 ✅

- `npm run typecheck / lint / test`（eslint-config-expo、tsc strict、jest-expo + RNTL；authStore 会话闭环 11 个用例）。
- CI：`.github/workflows/ci.yml` mobile job（install → typecheck → lint → test）。
- lockfile 提交；`eas.json`（development/preview/production）；assets 用模板真实图标（品牌化待做）。

## 未实现 📋（后续规划）

- 通知中心与推送（expo-notifications，依赖后端通知能力）。
- OAuth 深链登录、编辑器图片上传（content-service + expo-image-picker）、分享（expo-sharing）。
- EAS Build / TestFlight 发布流（`eas init` 拿真实 projectId 后启用 updates）。
- Android（v2）、visionOS（v3）。

## 技术栈（`mobile/package.json`）

| 技术                  | 版本   |
| --------------------- | ------ |
| Expo                  | ~57.0  |
| Expo Router           | ~57.0  |
| React Native          | 0.86.2 |
| React                 | 19.2.3 |
| @tanstack/react-query | ^5.101 |
| zustand               | ^5.0   |
| NativeWind            | ^4.2   |
| @shopify/flash-list   | 2.0.2  |
| axios                 | ^1.19  |

## 开发

```bash
cd mobile
npm install
cp .env.example .env        # 网关地址默认 http://localhost:8080（docker compose up 后可用）
npm run ios                 # iOS 模拟器；Android 模拟器需把 .env 改为 10.0.2.2
npm run typecheck && npm run lint && npm test
```

验证过的链路：登录 → 首页分页 → 详情 → 点赞/收藏 → 评论，全程携带 token；后端不可用时展示错误态 + 重试。
