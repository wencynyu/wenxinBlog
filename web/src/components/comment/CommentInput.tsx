'use client';

import { useState } from 'react';
import { TextArea, Button, Toast, Banner } from '@douyinfe/semi-ui';
import { IconSend } from '@douyinfe/semi-icons';
import Link from 'next/link';
import { useAuthStore } from '@/store/authStore';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import * as commentsApi from '@/lib/api/comments';

interface CommentInputProps {
  postId: string;
}

export default function CommentInput({ postId }: CommentInputProps) {
  const [content, setContent] = useState('');
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (content: string) => commentsApi.createComment(postId, content),
    onSuccess: () => {
      setContent('');
      queryClient.invalidateQueries({ queryKey: ['comments', postId] });
      Toast.success('评论成功');
    },
    onError: (error: any) => {
      Toast.error(error?.message || '评论失败');
    },
  });

  const handleSubmit = () => {
    if (!content.trim()) return;
    if (!isAuthenticated) {
      Toast.warning('请先登录');
      return;
    }
    mutation.mutate(content.trim());
  };

  if (!isAuthenticated) {
    return (
      <Banner
        type="info"
        description={
          <>
            登录后参与评论，
            <Link href="/login" className="text-primary-600 font-medium">
              去登录
            </Link>
          </>
        }
      />
    );
  }

  return (
    <div className="flex gap-3 items-end">
      <TextArea
        value={content}
        onChange={setContent}
        onEnterPress={handleSubmit}
        placeholder="写下你的评论..."
        maxCount={500}
        autosize={{ minRows: 1, maxRows: 4 }}
        disabled={mutation.isPending}
      />
      <Button
        icon={<IconSend />}
        theme="solid"
        onClick={handleSubmit}
        loading={mutation.isPending}
        disabled={!content.trim()}
      >
        发送
      </Button>
    </div>
  );
}
