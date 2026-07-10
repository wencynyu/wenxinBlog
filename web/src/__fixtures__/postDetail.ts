/**
 * 博文详情页演示数据 —— 仅用于开发/Storybook/测试，**不要**在生产页面中导入。
 */
export const MOCK_POSTS: Record<string, any> = {
  '1': {
    id: '1',
    title: 'Next.js 14 App Router 完全指南',
    content: `## 什么是 App Router？

App Router 是 Next.js 13+ 引入的全新路由系统，基于 React Server Components 构建。

### 核心概念

- **文件系统路由**: 使用 \`app/\` 目录替代 \`pages/\`
- **布局嵌套**: 通过 \`layout.tsx\` 实现共享布局
- **加载状态**: \`loading.tsx\` 自动处理 Suspense
- **错误处理**: \`error.tsx\` 优雅处理错误

### 路由约定

\`\`\`typescript
app/
├── page.tsx          // /
├── about/
│   └── page.tsx      // /about
└── layout.tsx        // 根布局
\`\`\``,
    summary: '深入探讨 Next.js 14 中 App Router 的核心概念、文件约定、路由机制以及最佳实践。',
    coverImage: '',
    authorId: 'mock-1',
    author: { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '' },
    tags: ['Next.js', 'React', '前端'],
    status: 'published',
    likesCount: 128,
    commentsCount: 32,
    isLiked: false,
    isFavorited: false,
    createdAt: '2026-03-25T10:30:00Z',
    updatedAt: '2026-03-25T10:30:00Z',
  },
};

export const DEFAULT_MOCK = {
  id: '0',
  title: '博文详情页（演示数据）',
  content: `## 这是一个演示博文

当前后端服务未连接，这里显示的是演示数据。`,
  summary: '演示数据 — 连接后端后显示真实博文内容。',
  coverImage: '',
  authorId: 'mock-0',
  author: { id: 'mock-0', username: 'WenxinBlog', displayName: 'WenxinBlog', avatar: '' },
  tags: ['演示', 'Next.js'],
  status: 'published',
  likesCount: 42,
  commentsCount: 10,
  isLiked: false,
  isFavorited: false,
  createdAt: '2026-03-27T00:00:00Z',
  updatedAt: '2026-03-27T00:00:00Z',
};

export const MOCK_RELATED = [
  {
    id: '3',
    title: 'TypeScript 5.0 新特性一览',
    likesCount: 89,
    author: { displayName: 'TS布道者' },
  },
  {
    id: '4',
    title: 'Tailwind CSS v4 实战技巧',
    likesCount: 312,
    author: { displayName: 'CSS魔法师' },
  },
  {
    id: '5',
    title: '构建高可用微服务架构实践',
    likesCount: 178,
    author: { displayName: '架构师老李' },
  },
];
