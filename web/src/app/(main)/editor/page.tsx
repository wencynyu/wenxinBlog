'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Input, Button, TagInput, Toast, Switch } from '@douyinfe/semi-ui';
import { IconArrowLeft } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import dynamic from 'next/dynamic';
import { useCreatePost } from '@/hooks/usePosts';

// 预览渲染较重（marked + highlight.js），按需加载
const MarkdownRenderer = dynamic(() => import('@/components/post/MarkdownRenderer'), {
  ssr: false,
});

export default function NewPostPage() {
  const router = useRouter();
  const createMutation = useCreatePost();

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [coverImage, setCoverImage] = useState('');
  const [showPreview, setShowPreview] = useState(false);

  const handleSubmit = async (publishStatus: 'draft' | 'published') => {
    if (!title.trim()) {
      Toast.warning('请输入标题');
      return;
    }
    if (!content.trim()) {
      Toast.warning('请输入内容');
      return;
    }

    try {
      const post = await createMutation.mutateAsync({
        title: title.trim(),
        content: content.trim(),
        tags,
        coverImage: coverImage || undefined,
        status: publishStatus,
      });
      Toast.success(publishStatus === 'published' ? '发布成功' : '草稿已保存');
      router.push(`/posts/${post.id}`);
    } catch (error: any) {
      Toast.error(error?.message || '操作失败');
    }
  };

  return (
    <MainLayout showSidebar={false}>
      <div className="max-w-5xl mx-auto">
        {/* 顶部操作栏 */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => router.back()}>
              返回
            </Button>
            <h1 className="font-serif text-xl font-bold text-ink">写博文</h1>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="eyebrow">{'// preview'}</span>
              <Switch checked={showPreview} onChange={setShowPreview} />
            </div>
            <Button
              theme="borderless"
              onClick={() => handleSubmit('draft')}
              loading={createMutation.isPending}
            >
              保存草稿
            </Button>
            <Button
              theme="solid"
              type="primary"
              onClick={() => handleSubmit('published')}
              loading={createMutation.isPending}
            >
              发布
            </Button>
          </div>
        </div>

        {/* 编辑区 */}
        <div className="grid grid-cols-1 gap-4">
          {/* 标题 */}
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="请输入博文标题..."
            className="w-full font-serif text-3xl font-bold text-ink placeholder-ink-faint outline-none border-none bg-transparent"
          />

          {/* 标签 */}
          <TagInput
            value={tags}
            onChange={setTags as any}
            placeholder="添加标签，回车确认"
            max={5}
            style={{ width: '100%' }}
          />

          {/* 封面图（可选） */}
          <Input value={coverImage} onChange={setCoverImage} placeholder="封面图链接（可选）" />

          {/* 内容区 */}
          {showPreview ? (
            <div className="bg-surface rounded-xl shadow-card p-6">
              <h1 className="font-serif text-2xl font-bold text-ink mb-4">{title || '标题预览'}</h1>
              {tags.length > 0 && (
                <div className="flex gap-2 mb-4">
                  {tags.map((tag) => (
                    <span
                      key={tag}
                      className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono bg-primary-50 text-primary-700"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              )}
              <MarkdownRenderer content={content || '* 内容预览区域...'} />
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              {/* 编辑器 */}
              <div className="relative">
                <textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder={
                    '使用 Markdown 编写博文内容...\n支持标准 Markdown 语法，包括代码高亮、表格、引用等'
                  }
                  className="w-full h-[600px] p-4 border border-hairline rounded-xl bg-surface text-ink-muted font-mono text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                />
              </div>

              {/* 实时预览 */}
              <div className="bg-surface rounded-xl shadow-card p-4 overflow-y-auto h-[600px]">
                <h1 className="font-serif text-xl font-bold text-ink mb-3">
                  {title || '标题预览'}
                </h1>
                <MarkdownRenderer content={content || '* 内容预览区域...'} />
              </div>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
