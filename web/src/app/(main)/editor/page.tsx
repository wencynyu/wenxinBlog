'use client';

import { useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import {
  Input,
  Button,
  TagInput,
  Toast,
  Switch,
  TextArea,
  Card,
  Typography,
  Tag,
} from '@douyinfe/semi-ui';
import { IconArrowLeft, IconImage } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import dynamic from 'next/dynamic';
import { useCreatePost } from '@/hooks/usePosts';
import { uploadFile } from '@/lib/api/content';

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
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const result = await uploadFile(file);
      setCoverImage(result.cdnUrl);
      Toast.success('图片上传成功');
    } catch (error: any) {
      Toast.error(error?.message || '上传失败');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

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
      <div className="max-w-5xl mx-auto overflow-x-hidden">
        {/* 顶部操作栏 */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => router.back()}>
              返回
            </Button>
            <Typography.Title heading={2} style={{ marginBottom: 0 }}>
              写博文
            </Typography.Title>
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
          <Input
            value={title}
            onChange={(value) => setTitle(value)}
            placeholder="请输入博文标题..."
            borderless
            style={{ fontSize: '1.875rem', fontWeight: 600 }}
          />

          {/* 标签 */}
          <TagInput
            value={tags}
            onChange={setTags as any}
            placeholder="添加标签，回车确认"
            max={5}
            style={{ width: '100%' }}
          />

          {/* 封面图（可选）— URL 输入 + 文件上传 */}
          <div className="flex gap-2">
            <Input
              value={coverImage}
              onChange={setCoverImage}
              placeholder="封面图链接（可选）"
              className="flex-1"
            />
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleFileUpload}
            />
            <Button
              icon={<IconImage />}
              theme="borderless"
              loading={uploading}
              onClick={() => fileInputRef.current?.click()}
            >
              上传图片
            </Button>
          </div>

          {/* 内容区 */}
          {showPreview ? (
            <Card bodyStyle={{ padding: 24 }}>
              <h1 className="font-serif text-2xl font-bold text-ink mb-4">{title || '标题预览'}</h1>
              {tags.length > 0 && (
                <div className="flex gap-2 mb-4">
                  {tags.map((tag) => (
                    <Tag key={tag} size="small" color="blue">
                      {tag}
                    </Tag>
                  ))}
                </div>
              )}
              <MarkdownRenderer content={content || '* 内容预览区域...'} />
            </Card>
          ) : (
            <div className="grid grid-cols-2 gap-4 overflow-hidden">
              {/* 编辑器 */}
              <div className="relative min-w-0">
                <TextArea
                  value={content}
                  onChange={(value) => setContent(value)}
                  placeholder={
                    '使用 Markdown 编写博文内容...\n支持标准 Markdown 语法，包括代码高亮、表格、引用等'
                  }
                  autosize={{ minRows: 24 }}
                  className="font-mono text-sm"
                />
              </div>

              {/* 实时预览 */}
              <Card
                bodyStyle={{ padding: 16 }}
                className="h-[600px] overflow-y-auto overflow-x-hidden min-w-0"
              >
                <h1 className="font-serif text-xl font-bold text-ink mb-3">
                  {title || '标题预览'}
                </h1>
                <MarkdownRenderer content={content || '* 内容预览区域...'} />
              </Card>
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
