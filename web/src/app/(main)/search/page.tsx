'use client';

import { useState, useRef, useCallback, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { Input, Tag, Avatar, Typography, Empty } from '@douyinfe/semi-ui';
import { IconSearch } from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import { useSearchPosts } from '@/hooks/useSearch';

const { Text } = Typography;

function SearchContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialQuery = searchParams.get('q') || '';

  const [query, setQuery] = useState(initialQuery);
  const inputRef = useRef<any>(null);

  const handleSearch = useCallback(
    (value: string) => {
      const trimmed = value.trim();
      setQuery(trimmed);
      if (trimmed) {
        router.replace(`/search?q=${encodeURIComponent(trimmed)}`);
      }
    },
    [router],
  );

  const { data, isLoading, isError } = useSearchPosts(query);

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-3xl mx-auto">
        <p className="eyebrow mb-3">{'// search'}</p>
        <div className="mb-6">
          <Input
            ref={inputRef}
            value={query}
            onChange={setQuery}
            onEnterPress={(e: any) => handleSearch(e.target.value)}
            prefix={<IconSearch />}
            placeholder="搜索博文、用户..."
            size="large"
          />
        </div>

        {!query ? (
          <div className="py-8">
            <Empty title="输入关键词开始搜索" description="支持搜索博文标题和内容" />
          </div>
        ) : isLoading ? (
          <div className="py-8">
            <div className="space-y-4">
              {[1, 2, 3].map((i) => (
                <div key={i} className="animate-pulse">
                  <div className="h-5 bg-gray-200 rounded w-3/4 mb-2" />
                  <div className="h-4 bg-gray-100 rounded w-full mb-1" />
                  <div className="h-4 bg-gray-100 rounded w-2/3" />
                </div>
              ))}
            </div>
          </div>
        ) : isError ? (
          <div className="py-12">
            <Empty title="搜索失败" description="search-service 暂不可用，请稍后再试" />
          </div>
        ) : data && data.items && data.items.length > 0 ? (
          <div className="space-y-4">
            <Text type="tertiary" size="small" className="font-mono">
              找到 {data.total} 条结果
            </Text>
            {data.items.map((post: any) => (
              <Link
                key={post.id}
                href={`/posts/${post.id}`}
                className="block p-4 rounded-xl border border-hairline hover:shadow-card transition-all bg-surface"
              >
                <h3 className="text-lg font-semibold text-ink mb-1 line-clamp-1 hover:text-primary-700">
                  {post.title}
                </h3>
                {post.summary && (
                  <p className="text-ink-muted text-sm line-clamp-2 mb-2">{post.summary}</p>
                )}
                <div className="flex items-center gap-3 text-ink-faint text-xs font-mono">
                  <span>{post.authorName || post.authorId || '未知'}</span>
                  <span>{post.likeCount} 赞</span>
                  <span>{post.viewCount} 阅读</span>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <div className="py-8">
            <Empty title="暂无结果" description={`没有找到与"${query}"相关的博文`} />
          </div>
        )}
      </div>
    </MainLayout>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={null}>
      <SearchContent />
    </Suspense>
  );
}
