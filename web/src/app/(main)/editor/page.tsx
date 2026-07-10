'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Input, Button, TagInput, Toast, Switch } from '@douyinfe/semi-ui';
import { IconArrowLeft } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import dynamic from 'next/dynamic';
// 预览渲染较重（marked + highlight.js），按需加载
const MarkdownRenderer = dynamic(() => import('@/components/post/MarkdownRenderer'), {
  ssr: false,
});
import { useCreatePost } from '@/hooks/usePosts';
import { useAuthStore } from '@/store/authStore';

export default function NewPostPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const createMutation = useCreatePost();

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [coverImage, setCoverImage] = useState('');
  const [status, setStatus] = useState<'draft' | 'published'>('draft');
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
            <h1 className="text-xl font-bold text-gray-900">写博文</h1>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="text-sm text-gray-500">预览</span>
              <Switch checked={showPreview} onChange={setShowPreview} />
            </div>
            <Button
              theme="borderless"
              onClick={() => handleSubmit('draft')}
              loading={createMutation.isLoading}
            >
              保存草稿
            </Button>
            <Button
              theme="solid"
              type="primary"
              onClick={() => handleSubmit('published')}
              loading={createMutation.isLoading}
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
            className="w-full text-3xl font-bold text-gray-900 placeholder-gray-300 outline-none border-none"
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
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h1 className="text-2xl font-bold mb-4">{title || '标题预览'}</h1>
              {tags.length > 0 && (
                <div className="flex gap-2 mb-4">
                  {tags.map((tag) => (
                    <span key={tag} className="text-sky-500 text-sm">
                      #{tag}
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
                  placeholder="使用 Markdown 编写博文内容...
支持标准 Markdown 语法，包括代码高亮、表格、引用等"
                  className="w-full h-[600px] p-4 border border-gray-200 rounded-lg text-gray-700 font-mono text-sm resize-none focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-transparent"
                />
              </div>

              {/* 实时预览 */}
              <div className="bg-white border border-gray-200 rounded-lg p-4 overflow-y-auto h-[600px]">
                <h1 className="text-xl font-bold mb-3">{title || '标题预览'}</h1>
                <MarkdownRenderer content={content || '* 内容预览区域...'} />
              </div>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
