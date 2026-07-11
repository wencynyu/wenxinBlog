'use client';

import { useState } from 'react';
import { Input, Button, Toast } from '@douyinfe/semi-ui';
import { IconSend } from '@douyinfe/semi-icons';
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
      <div className="bg-canvas rounded-xl p-4 text-center text-ink-muted text-sm">
        <a href="/login" className="text-primary-700 font-medium">
          登录
        </a>{' '}
        后参与评论
      </div>
    );
  }

  return (
    <div className="flex gap-3">
      <Input
        value={content}
        onChange={setContent}
        onEnterPress={handleSubmit}
        placeholder="写下你的评论..."
        maxLength={500}
        showClear
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
