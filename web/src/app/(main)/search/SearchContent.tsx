'use client';

import { useState, useRef, useCallback } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { Input, Typography, Empty, Skeleton, Card } from '@douyinfe/semi-ui';
import { IconSearch } from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';
import { useSearchPosts } from '@/hooks/useSearch';

const { Text, Title, Paragraph } = Typography;

export default function SearchContent() {
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
                <div key={i}>
                  <Skeleton.Title style={{ width: '60%', height: 20, marginBottom: 8 }} />
                  <Skeleton.Paragraph rows={2} />
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
              <Link key={post.id} href={`/posts/${post.id}`}>
                <Card shadows="hover" bodyStyle={{ padding: 16 }}>
                  <Title heading={5} ellipsis={{ rows: 1 }} style={{ marginBottom: 4 }}>
                    {post.title}
                  </Title>
                  {post.summary && (
                    <Paragraph type="tertiary" ellipsis={{ rows: 2 }} style={{ marginBottom: 8 }}>
                      {post.summary}
                    </Paragraph>
                  )}
                  <div className="flex items-center gap-3">
                    <Text type="tertiary" size="small">
                      {post.author?.displayName || post.author?.username || '未知'}
                    </Text>
                    <Text type="tertiary" size="small">
                      {post.likeCount} 赞
                    </Text>
                    <Text type="tertiary" size="small">
                      {post.viewCount} 阅读
                    </Text>
                  </div>
                </Card>
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
