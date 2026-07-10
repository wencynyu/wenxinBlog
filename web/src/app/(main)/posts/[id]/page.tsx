'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import {
  Avatar,
  Tag,
  Button,
  Toast,
  Typography,
  Divider,
} from '@douyinfe/semi-ui';
import {
  IconLikeHeart,
  IconStar,
  IconStarStroked,
  IconEdit,
  IconArrowLeft,
  IconShareStroked,
} from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import MarkdownRenderer from '@/components/post/MarkdownRenderer';
import CommentInput from '@/components/comment/CommentInput';
import CommentList from '@/components/comment/CommentList';
import { useToggleLike, useToggleFavorite } from '@/hooks/usePosts';
import { getPost } from '@/lib/api/posts';
import { getRelatedPosts } from '@/lib/api/recommend';
import { useAuthStore } from '@/store/authStore';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Title, Text } = Typography;

const MOCK_POSTS: Record<string, any> = {
  '1': {
    id: '1', title: 'Next.js 14 App Router 完全指南',
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
├── blog/
│   ├── page.tsx      // /blog
│   └── [slug]/
│       └── page.tsx  // /blog/:slug
└── layout.tsx        // 根布局
\`\`\`

### Server Components

Server Components 是 App Router 的基石：

\`\`\`tsx
// 这个组件在服务端渲染
async function PostList() {
  const posts = await db.posts.findMany();
  return (
    <ul>
      {posts.map(post => <li key={post.id}>{post.title}</li>)}
    </ul>
  );
}
\`\`\`

> 注意：Server Components 不能使用 hooks、事件处理器或浏览器 API。

### 最佳实践

1. 默认使用 Server Components
2. 只在需要交互的地方使用 \`'use client'\`
3. 利用 \`loading.tsx\` 提升用户体验
4. 使用 \`Route Groups\` 组织代码结构`,
    summary: '深入探讨 Next.js 14 中 App Router 的核心概念、文件约定、路由机制以及最佳实践。',
    coverImage: '', authorId: 'mock-1',
    author: { id: 'mock-1', username: '技术小王', displayName: '技术小王', avatar: '' },
    tags: ['Next.js', 'React', '前端'],
    status: 'published', likesCount: 128, commentsCount: 32,
    isLiked: false, isFavorited: false,
    createdAt: '2026-03-25T10:30:00Z', updatedAt: '2026-03-25T10:30:00Z',
  },
  '2': {
    id: '2', title: 'React Server Components 深度解析',
    content: `## React Server Components (RSC)

Server Components 是 React 团队推出的革命性特性。

### 为什么需要 RSC？

传统的 SSR 存在以下问题：
- 客户端需要重新水合整个页面
- JavaScript bundle 过大
- 无法直接访问服务端资源

### RSC 的优势

| 特性 | 传统的 CSR/SSR | RSC |
|------|---------------|-----|
| Bundle 大小 | 大 | 小 |
| 服务端数据获取 | 需要额外 API | 直接访问 |
| 交互性 | 强 | 按需选择 |
| SEO | 需要额外处理 | 内置支持 |

\`\`\`jsx
// Server Component - 在服务端执行
async function UserProfile({ userId }) {
  const user = await getUser(userId);
  return <Avatar src={user.avatar} />;
}

// Client Component - 需要交互
'use client';
function LikeButton({ postId }) {
  const [liked, setLiked] = useState(false);
  return <button onClick={() => setLiked(!liked)}>赞</button>;
}
\`\`\``,
    summary: 'Server Components 是 React 的革命性特性，本文将带你从原理到实践全面掌握。',
    coverImage: '', authorId: 'mock-2',
    author: { id: 'mock-2', username: '前端达人', displayName: '前端达人', avatar: '' },
    tags: ['React', 'Server Components', '性能优化'],
    status: 'published', likesCount: 256, commentsCount: 67,
    isLiked: false, isFavorited: false,
    createdAt: '2026-03-24T08:15:00Z', updatedAt: '2026-03-24T08:15:00Z',
  },
};

// Default mock for any ID
const DEFAULT_MOCK = {
  id: '0', title: '博文详情页（演示数据）',
  content: `## 这是一个演示博文

当前后端服务未连接，这里显示的是演示数据。

连接后端后，这里会展示真实的博文内容。

### 功能特点

- **Markdown 渲染**: 支持完整的 Markdown 语法
- **代码高亮**: 自动语法高亮
- **点赞收藏**: 登录后可交互
- **评论系统**: 支持发表评论

\`\`\`javascript
console.log('Hello WenxinBlog!');
\`\`\`

> 连接后端 API (localhost:8080) 后即可查看真实内容。`,
  summary: '演示数据 — 连接后端后显示真实博文内容。',
  coverImage: '', authorId: 'mock-0',
  author: { id: 'mock-0', username: 'WenxinBlog', displayName: 'WenxinBlog', avatar: '' },
  tags: ['演示', 'Next.js'],
  status: 'published', likesCount: 42, commentsCount: 10,
  isLiked: false, isFavorited: false,
  createdAt: '2026-03-27T00:00:00Z', updatedAt: '2026-03-27T00:00:00Z',
};

const MOCK_RELATED = [
  { id: '3', title: 'TypeScript 5.0 新特性一览', likesCount: 89, author: { displayName: 'TS布道者' } },
  { id: '4', title: 'Tailwind CSS v4 实战技巧', likesCount: 312, author: { displayName: 'CSS魔法师' } },
  { id: '5', title: '构建高可用微服务架构实践', likesCount: 178, author: { displayName: '架构师老李' } },
];

export default function PostDetailPage() {
  const params = useParams();
  const router = useRouter();
  const postId = params.id as string;
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const toggleLike = useToggleLike();
  const toggleFavorite = useToggleFavorite();

  const [post, setPost] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [relatedPosts, setRelatedPosts] = useState<any[]>([]);
  const [useMock, setUseMock] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);

    // Try API first
    getPost(postId)
      .then((data) => {
        if (!cancelled && data) {
          setPost(data);
        } else if (!cancelled) {
          // Use mock data
          const mock = MOCK_POSTS[postId] || { ...DEFAULT_MOCK, id: postId };
          setPost(mock);
          setUseMock(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          const mock = MOCK_POSTS[postId] || { ...DEFAULT_MOCK, id: postId };
          setPost(mock);
          setUseMock(true);
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    // Try related posts
    getRelatedPosts(postId, 5)
      .then((data) => {
        if (!cancelled && data && data.length > 0) {
          setRelatedPosts(data);
        } else if (!cancelled) {
          setRelatedPosts(MOCK_RELATED);
        }
      })
      .catch(() => {
        if (!cancelled) setRelatedPosts(MOCK_RELATED);
      });

    return () => { cancelled = true; };
  }, [postId]);

  const handleShare = async () => {
    try {
      if (navigator.share) {
        await navigator.share({ title: post?.title, url: window.location.href });
      } else {
        await navigator.clipboard.writeText(window.location.href);
        Toast.success('链接已复制');
      }
    } catch {}
  };

  if (isLoading) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto">
          <div className="animate-pulse">
            <div className="h-8 bg-gray-200 rounded w-16 mb-6" />
            <div className="h-8 bg-gray-200 rounded w-4/5 mb-4" />
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 bg-gray-200 rounded-full" />
              <div className="h-4 bg-gray-200 rounded w-24" />
            </div>
            <div className="space-y-3">
              <div className="h-4 bg-gray-100 rounded w-full" />
              <div className="h-4 bg-gray-100 rounded w-full" />
              <div className="h-4 bg-gray-100 rounded w-3/4" />
            </div>
          </div>
        </div>
      </MainLayout>
    );
  }

  if (!post) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto text-center py-20">
          <Title heading={3}>博文不存在</Title>
          <Button theme="solid" onClick={() => router.push('/')} style={{ marginTop: 16 }}>
            返回首页
          </Button>
        </div>
      </MainLayout>
    );
  }

  const isAuthor = user && user.id === post.authorId;

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-3xl mx-auto">
        {/* 返回按钮 */}
        <button
          onClick={() => router.back()}
          className="flex items-center text-gray-500 hover:text-gray-700 mb-6 text-sm"
        >
          <IconArrowLeft className="mr-1" />
          返回
        </button>

        <article>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-4">{post.title}</h1>

          {/* 作者信息 */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center">
              <Link href={`/user/${post.authorId}`}>
                <Avatar
                  size="default"
                  src={post.author?.avatar}
                  alt={post.author?.displayName || post.author?.username}
                >
                  {(post.author?.displayName || post.author?.username || 'U')[0]}
                </Avatar>
              </Link>
              <div className="ml-3">
                <Link href={`/user/${post.authorId}`}>
                  <Text strong className="text-gray-900 dark:text-gray-100">
                    {post.author?.displayName || post.author?.username}
                  </Text>
                </Link>
                <br />
                <Text type="tertiary" size="small">
                  {dayjs(post.createdAt).format('YYYY-MM-DD HH:mm')}
                </Text>
              </div>
            </div>

            {isAuthor && (
              <div className="flex gap-2">
                <Link href={`/editor/${post.id}`}>
                  <Button icon={<IconEdit />} theme="borderless" size="small">
                    编辑
                  </Button>
                </Link>
              </div>
            )}
          </div>

          {/* 封面图 */}
          {post.coverImage && (
            <div className="mb-6 rounded-xl overflow-hidden">
              <img src={post.coverImage} alt={post.title} className="w-full object-cover" />
            </div>
          )}

          {/* 标签 */}
          {post.tags && post.tags.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-6">
              {post.tags.map((tag: string) => (
                <Link key={tag} href={`/posts?tag=${encodeURIComponent(tag)}`}>
                  <Tag color="cyan" size="large">#{tag}</Tag>
                </Link>
              ))}
            </div>
          )}

          {/* 正文 */}
          {useMock && <p className="text-gray-400 text-sm mb-4">（演示数据 — 连接后端后显示真实内容）</p>}
          <MarkdownRenderer content={post.content} />

          {/* 操作栏 */}
          <Divider />
          <div className="flex items-center justify-center gap-6 py-4">
            <Button
              icon={<IconLikeHeart />}
              theme={post.isLiked ? 'solid' : 'borderless'}
              type={post.isLiked ? 'danger' : 'tertiary'}
              onClick={() => {
                if (!isAuthenticated) { Toast.warning('请先登录'); return; }
                toggleLike.mutate({ id: post.id, isLiked: post.isLiked });
              }}
            >
              {post.likesCount || 0}
            </Button>

            <Button
              icon={post.isFavorited ? <IconStar /> : <IconStarStroked />}
              theme={post.isFavorited ? 'solid' : 'borderless'}
              type={post.isFavorited ? 'warning' : 'tertiary'}
              onClick={() => {
                if (!isAuthenticated) { Toast.warning('请先登录'); return; }
                toggleFavorite.mutate({ id: post.id, isFavorited: post.isFavorited });
              }}
            >
              收藏
            </Button>

            <Button icon={<IconShareStroked />} theme="borderless" onClick={handleShare}>
              分享
            </Button>
          </div>

          {/* 评论区 */}
          <Divider />
          <div className="mb-8">
            <Title heading={4} className="mb-4">
              评论 ({post.commentsCount || 0})
            </Title>
            <CommentInput postId={postId} />
          </div>
          <CommentList postId={postId} />
        </article>

        {/* 相关推荐 */}
        {relatedPosts && relatedPosts.length > 0 && (
          <>
            <Divider />
            <div>
              <Title heading={4} className="mb-4">相关推荐</Title>
              <div className="space-y-3">
                {relatedPosts.map((rp: any) => (
                  <Link
                    key={rp.id}
                    href={`/posts/${rp.id}`}
                    className="block p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <Text strong className="line-clamp-1">{rp.title}</Text>
                    <div className="flex items-center gap-3 mt-1">
                      <Text type="tertiary" size="small">
                        {rp.author?.displayName || rp.author?.username}
                      </Text>
                      <Text type="tertiary" size="small">{rp.likesCount} 赞</Text>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </MainLayout>
  );
}
