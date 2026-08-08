'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  Input,
  Button,
  TagInput,
  Toast,
  Switch,
  Skeleton,
  TextArea,
  Card,
  Typography,
  Tag,
} from '@douyinfe/semi-ui';
import { IconArrowLeft } from '@douyinfe/semi-icons';
import MainLayout from '@/components/layout/MainLayout';
import dynamic from 'next/dynamic';
import { usePost, useUpdatePost } from '@/hooks/usePosts';
import { useAuthStore } from '@/store/authStore';

// 预览渲染较重（marked + highlight.js），按需加载
const MarkdownRenderer = dynamic(() => import('@/components/post/MarkdownRenderer'), {
  ssr: false,
});

export default function EditPostPage() {
  const params = useParams();
  const router = useRouter();
  const postId = params.id as string;
  const user = useAuthStore((state) => state.user);
  const { data: post, isLoading } = usePost(postId);
  const updateMutation = useUpdatePost();

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [coverImage, setCoverImage] = useState('');
  const [showPreview, setShowPreview] = useState(false);

  useEffect(() => {
    if (post) {
      setTitle(post.title);
      setContent(post.content);
      setTags(post.tags || []);
      setCoverImage(post.coverImage || '');
    }
  }, [post]);

  const handleSubmit = async (status: 'draft' | 'published') => {
    if (!title.trim()) {
      Toast.warning('请输入标题');
      return;
    }
    if (!content.trim()) {
      Toast.warning('请输入内容');
      return;
    }

    try {
      const updatedPost = await updateMutation.mutateAsync({
        id: postId,
        data: {
          title: title.trim(),
          content: content.trim(),
          tags,
          coverImage: coverImage || undefined,
          status,
        },
      });
      Toast.success(status === 'published' ? '发布成功' : '草稿已保存');
      router.push(`/posts/${updatedPost.id}`);
    } catch (error: any) {
      Toast.error(error?.message || '操作失败');
    }
  };

  if (isLoading) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-5xl mx-auto overflow-x-hidden">
          <Skeleton.Title style={{ width: 200, height: 36, marginBottom: 24 }} />
          <Skeleton.Title style={{ width: '60%', height: 40, marginBottom: 16 }} />
          <Skeleton.Paragraph style={{ width: '100%' }} />
        </div>
      </MainLayout>
    );
  }

  if (!post) {
    return (
      <MainLayout showSidebar={false}>
        <div className="max-w-3xl mx-auto text-center py-20">
          <h2 className="font-serif text-2xl font-bold text-ink mb-4">博文不存在</h2>
          <Button theme="solid" onClick={() => router.push('/')}>
            返回首页
          </Button>
        </div>
      </MainLayout>
    );
  }

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
              编辑博文
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
              loading={updateMutation.isPending}
            >
              保存草稿
            </Button>
            <Button
              theme="solid"
              type="primary"
              onClick={() => handleSubmit('published')}
              loading={updateMutation.isPending}
            >
              发布
            </Button>
          </div>
        </div>

        {/* 编辑区 */}
        <div className="grid grid-cols-1 gap-4">
          <Input
            value={title}
            onChange={(value) => setTitle(value)}
            placeholder="请输入博文标题..."
            borderless
            style={{ fontSize: '1.875rem', fontWeight: 600 }}
          />

          <TagInput
            value={tags}
            onChange={setTags as any}
            placeholder="添加标签，回车确认"
            max={5}
            style={{ width: '100%' }}
          />

          <Input value={coverImage} onChange={setCoverImage} placeholder="封面图链接（可选）" />

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
              <div className="relative min-w-0">
                <TextArea
                  value={content}
                  onChange={(value) => setContent(value)}
                  placeholder="使用 Markdown 编写博文内容..."
                  autosize={{ minRows: 24 }}
                  className="font-mono text-sm"
                />
              </div>
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
