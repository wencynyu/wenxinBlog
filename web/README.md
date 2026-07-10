# WenxinBlog Web

博文平台 Web 前端 (Next.js 14 + Semi-Design)

## 功能特性

- 📝 博文阅读/发布
- 🔍 全文搜索
- 👤 用户主页
- 🔔 通知系统
- 🌙 暗色模式
- 📱 响应式设计

## 技术栈

- **框架**: Next.js 14 (App Router)
- **UI**: Semi-Design
- **状态管理**: Zustand
- **数据请求**: React Query
- **样式**: Tailwind CSS
- **Markdown**: marked + highlight.js

## 目录结构

```
src/
├── app/              # App Router页面
│   ├── (main)/       # 主布局页面
│   ├── (auth)/       # 认证相关页面
│   ├── api/          # API路由
│   └── layout.tsx
├── components/       # 组件
│   ├── layout/       # 布局组件
│   ├── blog/         # 博文组件
│   └── ui/           # 通用组件
├── lib/              # 工具函数
├── hooks/            # 自定义Hooks
└── types/            # TypeScript类型
```

## 环境变量

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_OSS_URL=http://localhost:9000
```

## 开发

```bash
npm install
npm run dev
```

访问 http://localhost:3000

## 构建

```bash
npm run build
npm start
```

## SEO

- 自动sitemap生成
- Meta标签优化
- 结构化数据
- Open Graph支持
