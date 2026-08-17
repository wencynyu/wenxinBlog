# Mobile 开发文档

面向本仓库贡献者的实现说明。功能现状见 `docs/frontend/mobile.md`，快速上手见 `README.md`。

## 与 web 端的复用关系

逻辑层四层从 `web/src/` 移植，端点、信封（`ApiResponse{code,message,data}` / `PaginatedResponse`）、token key（`auth_token`）与 web 完全一致：

| 层                       | 来源                         | 适配点                                                                                                                 |
| ------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `src/types/`             | `web/src/types/` 原样复制    | 无                                                                                                                     |
| `src/lib/api/`           | `web/src/lib/api/` 同构移植  | token 读写改 SecureStore（异步拦截器）；401 由 `window.location` 改回调注册；丢弃 server.ts / cookie 同步              |
| `src/hooks/`             | `web/src/hooks/` 同构移植    | `next/navigation` → `expo-router`；新增 `useInfinitePosts`                                                             |
| `src/store/authStore.ts` | `web/src/store/authStore.ts` | persist 存 AsyncStorage；token 不进 persist（SecureStore 为准）；`checkAuth` 升级为 `bootstrap()`：`/auth/me` 真实验证 |

后续新增 API/类型时，**先在 web 端定契约，再同步两侧**，避免两端漂移。

## 认证闭环

```
启动  → splash 保持 → persist 水化(AsyncStorage) → bootstrap()
                                              ├─ SecureStore 有 token → GET /auth/me
                                              │    ├─ 200 → user 恢复，进 (tabs)
                                              │    └─ 失败 → 清 token，进 (auth)/login
                                              └─ 无 token → 直接进 (auth)/login
登录  → POST /auth/login → setSession（token 落 SecureStore）→ replace (tabs)
运行  → 任意请求 401 → 拦截器清 SecureStore token → onUnauthorized 回调
        （app/_layout 注册）→ resetSession + replace login
```

实现细节：

- `client.ts` 与 `authStore` 不互相 import（避免循环依赖）；401 的路由跳转通过 `setUnauthorizedHandler` 由 app 层注入。
- 登录/注册的 mutation 在 `src/hooks/useAuth.ts`，成功后 `router.replace('/(tabs)')`；根布局的认证门（`useAuthGate`）负责所有其余路径的守卫。

## 环境与端口

- `EXPO_PUBLIC_API_URL`（构建期内联）：默认 `http://localhost:8080`。
  - iOS 模拟器与同网段真机：`http://<mac-ip>:8080`
  - Android 模拟器：`http://10.0.2.2:8080`
- 后端用仓库根 `docker compose up`（override 文件把网关映射到宿主机 8080）。

## 主题

- `tailwind.config.js`：`primary`（Semi 蓝 #0077FA）/ `accent` 色板从 web 端复制；`ink/canvas/surface/hairline` 引用 `global.css` 的 CSS 变量。
- 深色模式：`app.json userInterfaceStyle: automatic` + `global.css` 的 `prefers-color-scheme` 变量翻转 + NativeWind `dark:` 变体；导航栏颜色在 `app/(tabs)/_layout.tsx` 用 `useColorScheme` 手动适配。
- Markdown 样式在 `app/posts/[id].tsx` 的 `useMarkdownStyles`（跟随深色模式）。

## 测试与 CI

- jest-expo + @testing-library/react-native；原生模块（SecureStore/AsyncStorage）在 `jest.setup.js` 内存模拟。
- 已覆盖：authStore 会话闭环（bootstrap 三分支、login 成败、注册自动登录、logout 容错）、posts API 信封解包。
- CI：`.github/workflows/ci.yml` 的 mobile job（typecheck + lint + test）。

## 已知决策与坑

- **babel.config.js 必须显式存在**（2026-08-18 踩坑）：NativeWind 的样式生效依赖 JSX 编译走 `nativewind/jsx-runtime`。SDK 57 模板没有 babel.config.js，且 tsconfig 里的 `jsxImportSource: "nativewind"` 在本项目中没有被 babel-preset-expo 读取——结果是 **app 全部 className 被 RN 静默丢弃（整 App 无样式但不报错）**，`expo export` 照样成功（极具迷惑性）。修复：`npm i -D babel-preset-expo` + babel.config.js 显式传 `jsxImportSource: 'nativewind'`。诊断方法：`expo export --dev` 后在 bundle 里 grep `nativewind/jsx-runtime`（app 代码应引用它而非 `react/jsx-runtime`）。**改 babel 配置后必须 `expo start --clear` 重启 Metro，否则 transform 缓存继续吐旧产物。**
- FlashList v2 与 v1 的 API 差异（2026-08-18 踩坑）：`estimatedItemSize` 已移除（v2 自动估算）；**`contentContainerStyle` 整个 prop 不存在**（v1 的），传入被静默忽略——gap/padding 全部失效，卡片贴边粘连且不报错。v2 的正确做法：间距用 `ItemSeparatorComponent`，边距放进 renderItem 的外层 View。全项目卡片列表统一 12px 间距（`PostCardGap` 组件 / `gap-3`）。
- TypeScript 6（SDK 57 默认）：`baseUrl` 已废弃，paths 用相对路径 `"./src/*"`。
- `expo-env.d.ts` / `nativewind-env.d.ts` 由 expo 生成且被 gitignore；`global.d.ts` 承担 expo/nativewind/jest 的类型引用，随仓库提交供 CI typecheck。
- 编辑器（发布博文）阶段 3 提供，先用 web 端 `/editor`。
- EAS：`eas.json` 就绪，但 `app.json` 的 `extra.eas.projectId` 与 `updates.url` 需在 `eas init` 后回填真实值（当前刻意未配假值）。
