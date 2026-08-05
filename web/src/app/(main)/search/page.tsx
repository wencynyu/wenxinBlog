'use client';

import dynamic from 'next/dynamic';

// 搜索页用 ssr:false 动态加载：避免 SSR streaming 阶段 Semi Input 的 hydration mismatch
// 导致 flight data 泄漏成可见文本（用户在页面看到 2:I[... / --semi-color 字符串）。
// 纯 client 渲染则无 streaming flight data，从根上消除泄漏路径。搜索页无 SEO 需求。
const SearchContent = dynamic(() => import('./SearchContent'), {
  ssr: false,
  loading: () => (
    <div className="max-w-3xl mx-auto py-20 text-center text-ink-faint">加载中...</div>
  ),
});

export default function SearchPage() {
  return <SearchContent />;
}
