> 最近更新：2026-08-02（对照实际代码核对）

# Mobile（React Native + Expo）

> ⚠️ **当前状态：早期脚手架 / POC，未完成。**
>
> 仓库根 `mobile/` 仅包含一个最小可运行的 Expo 壳与少量页面，git 跟踪约 13 个文件。本文档早先描述的大量功能（推送通知、图片相册、富文本编辑、Flash List、EAS 发布流等）**当时均为规划，并未落地**。本文件据实重写：已实现的标 ✅，已声明但无代码的标 📋，预留空目录标 ∅。

## 实际实现情况

代码位于仓库根 `mobile/`。

### 已实现 ✅

- **Expo Router 壳**：`app/_layout.tsx`（Stack）、`app/(tabs)/_layout.tsx`（Tabs）。
- **首页 Feed**：`app/(tabs)/index/page.tsx` —— 用 **`FlatList`**（非 Flash List）+ `useInfiniteQuery` 拉取 `http://localhost:8080/api/v1/posts`，展示标题 / 摘要 / 浏览数 & 点赞数，点击跳 `/posts/[id]`。
- **登录 / 注册页**：`app/(auth)/login/page.tsx`、`app/(auth)/register/page.tsx`。
- **API 客户端**：`lib/api/client.ts` —— 基于 `fetch` 的薄封装，token 存 `expo-secure-store`（`auth_token`），401 清 token。
- **状态**：`store/authStore.ts`（Zustand：user / isAuthenticated / login / logout / loadUser）。
- **类型**：`types/index.ts`。

### 已声明但未实现 📋（路由在 layout 里登记，但无 `page.tsx`）

- `(tabs)` 下的 discover / create / notifications / profile（只有 index 有页面）。
- Stack 下的 `posts/[id]` / `posts/new` / `user/[id]` / `search` / `settings`。

### 预留空目录 ∅（无任何代码）

`components/{blog,common,layout,user}`、`hooks/`、`constants/`、`assets/`，以及 `src/{components,hooks,navigation,screens,services,store,types,utils}`。

## 技术栈（`mobile/package.json`）

| 技术                  | 版本   | 状态 |
| --------------------- | ------ | ---- |
| Expo                  | ~51.0  | ✅   |
| Expo Router           | ~3.5   | ✅   |
| React Native          | 0.74.1 | ✅   |
| React                 | 18.2   | ✅   |
| @tanstack/react-query | ^5.45  | ✅   |

> ⚠️ 已知问题：代码实际用到 `zustand`、`expo-secure-store`、`@expo/vector-icons`，但 `package.json` **未声明**这些依赖——脚手架不完整，安装运行前需手动补齐。

早先文档提及的 Semi-Design Mobile（Halation）、@shopify/flash-list、expo-image、expo-notifications、expo-image-picker、expo-sharing 等**均未引入**。

## 后续规划（未实现，仅作方向参考）

- 补齐 discover / profile / 博文详情 / 发布 / 搜索 / 设置等页面。
- 列表升级到 Flash List，图片用 expo-image。
- 推送通知（expo-notifications）、图片上传（expo-image-picker）、分享（expo-sharing）。
- EAS Build / TestFlight / App Store 发布流。
- Android（v2）、visionOS（v3）。

## 开发

```bash
cd mobile
npm install
npm start      # expo start
# 按 i 启动 iOS 模拟器（需先补齐缺失依赖与原生环境）
```
