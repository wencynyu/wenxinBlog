'use client';

import { useState, useRef, useCallback, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { Input, Tabs, TabPane, Tag, Avatar, Typography, Empty } from '@douyinfe/semi-ui';
import { IconSearch } from '@douyinfe/semi-icons';
import Link from 'next/link';
import MainLayout from '@/components/layout/MainLayout';

const { Text } = Typography;

function SearchContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialQuery = searchParams.get('q') || '';

  const [query, setQuery] = useState(initialQuery);
  const [activeTab, setActiveTab] = useState('posts');
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

  // search-service 未运行时显示空状态
  const searchAvailable = false;

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

        {query ? (
          searchAvailable ? (
            <Tabs activeKey={activeTab} onChange={setActiveTab} type="line">
              <TabPane tab="博文" itemKey="posts">
                <div className="py-4">
                  <Empty title="暂无结果" description={`没有找到与"${query}"相关的博文`} />
                </div>
              </TabPane>
              <TabPane tab="用户" itemKey="users">
                <div className="py-4">
                  <Empty title="暂无结果" description={`没有找到与"${query}"相关的用户`} />
                </div>
              </TabPane>
            </Tabs>
          ) : (
            <div className="py-12">
              <Empty title="搜索服务暂不可用" description="search-service 未启动，请稍后再试" />
            </div>
          )
        ) : (
          <div className="py-8">
            <Empty title="输入关键词开始搜索" description="支持搜索博文标题和用户" />
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
