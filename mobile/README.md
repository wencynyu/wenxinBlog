# WenxinBlog Mobile

博文平台的 iOS 客户端（React Native + Expo）。2026-08-18 基于 **Expo SDK 57** 重建：逻辑层从 web 端移植（与后端 `/api/v1/*` 契约一致），UI 用 React Native + NativeWind 重写。

## 当前状态

功能完整闭环已可用：**登录/注册 → 首页 feed（无限分页）→ 发现（热榜/推荐流）→ 搜索（博文+用户）→ 博文详情（Markdown/点赞/收藏/评论/相关阅读）→ 发博文/编辑 → 用户主页（关注）→ 设置 → 退出登录**，带路由认证门、401 全局处理、深色模式。通知为占位页（依赖后端通知能力），OAuth/推送/编辑器图片上传为后续规划，详见 `docs/frontend/mobile.md`。

## 技术栈

- Expo SDK 57（React 19.2 / RN 0.86 / New Architecture）+ Expo Router v6
- TanStack Query v5（数据层）+ zustand v5（会话状态，persist 到 AsyncStorage）
- NativeWind v4（Tailwind 3，色板与 web 端一致）+ FlashList v2 + expo-image
- token 存 expo-secure-store（iOS Keychain）

## 开发

```bash
cd mobile
npm install
cp .env.example .env   # EXPO_PUBLIC_API_URL 默认 http://localhost:8080（docker compose up 的网关）
npm run ios            # iOS 模拟器；Android 模拟器请把 .env 改为 http://10.0.2.2:8080
```

质量检查（CI 同款）：

```bash
npm run typecheck
npm run lint
npm test
```

## 目录结构

```
mobile/
├── app/                      # expo-router 路由
│   ├── _layout.tsx           # 根布局：Provider + 认证门 + 401 回调 + 启动恢复会话
│   ├── (auth)/               # login / register
│   ├── (tabs)/               # 首页 feed / 发现 / 发布(→编辑器) / 通知 / 我的
│   ├── posts/new.tsx         # 发博文
│   ├── posts/[id]/           # index 详情（Markdown+评论+相关阅读）/ edit 编辑
│   ├── user/[id].tsx         # 用户主页（关注+作品）
│   ├── search.tsx            # 搜索（热搜/标签/历史+结果）
│   └── settings.tsx          # 设置（编辑资料）
├── src/
│   ├── api/                  # axios client（信封解包/token/401）+ auth/posts/comments/users/search/recommend
│   ├── hooks/                # TanStack Query 封装（与 web 端同构）
│   ├── store/                # zustand authStore（token→SecureStore，user→AsyncStorage）
│   ├── components/           # PostCard / PostEditor / BrandMark / 表单 / 三态组件
│   ├── constants/            # key 与 API 地址常量
│   └── types/                # 与 web 端一致的领域类型
├── babel.config.js           # jsxImportSource 钉给 nativewind（缺它全 app 无样式，见 DEV.md）
├── global.css                # 语义色 CSS 变量（亮/暗）
└── tailwind.config.js        # NativeWind + web 同款色板
```

## 发布（待 EAS 初始化）

`eas.json` 已备好 development/preview/production 三个 profile；执行 `eas init`（需 Expo 账号）拿到真实 projectId 后，再在 `app.json` 启用 `updates`。
