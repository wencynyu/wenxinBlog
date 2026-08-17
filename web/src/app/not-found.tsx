import Link from 'next/link';

/** 全局 404：未知路由与 notFound() 都落在这里。
 *  保持 Server Component（不要引 Semi 类组件，会拖垮所有页面的 RSC 渲染），用原生元素 + Tailwind */
export default function NotFoundPage() {
  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center py-20 px-4">
      <h1 className="font-mono text-6xl font-bold text-ink mb-4">404</h1>
      <p className="text-ink-muted mb-8">这个页面不存在或已被移除</p>
      <Link
        href="/"
        className="px-5 py-2 rounded-[0.625rem] bg-primary-600 text-white font-medium hover:bg-primary-700 transition-colors"
      >
        返回首页
      </Link>
    </div>
  );
}
