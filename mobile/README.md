# WenxinBlog Mobile

博文平台 iOS 客户端 (React Native + Expo)

## 功能特性

- 📱 iOS原生体验
- 📝 博文阅读/发布
- 📷 图片上传
- 🔔 推送通知
- 👤 个人中心
- 🔍 内容搜索

## 技术栈

- **框架**: React Native 0.74 + Expo 51
- **路由**: Expo Router (File-based)
- **UI**: Semi-Design Mobile (Halation)
- **状态管理**: Zustand
- **数据请求**: TanStack Query
- **安全存储**: Expo SecureStore

## 目录结构

```
src/
├── app/              # Expo Router页面
├── components/       # 组件
│   ├── common/       # 通用组件
│   └── blog/         # 博文组件
├── screens/          # 页面组件
├── navigation/       # 导航配置
├── hooks/            # 自定义Hooks
├── services/         # API服务
├── store/            # 状态管理
├── types/            # TypeScript类型
└── utils/            # 工具函数
```

## 环境变量

创建 `app.config.js`:

```js
export default {
  expo: {
    extra: {
      apiUrl: process.env.API_URL || 'http://localhost:8080',
      ossUrl: process.env.OSS_URL || 'http://localhost:9000',
    },
  },
}
```

## 开发

```bash
npm install
npm start
```

按 `i` 打开 iOS 模拟器

## 构建

```bash
# EAS Build (推荐)
eas build --platform ios

# 本地构建 (需要Mac)
expo run:ios
```

## iOS优先

当前版本优先支持iOS，Android和visionOS作为后续迭代。

## 推送通知

配置APNs证书后，使用Expo Notifications:
```bash
npx expo install expo-notifications
```
