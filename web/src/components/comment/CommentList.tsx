'use client';

import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { Avatar, Button, Toast, Skeleton, Empty, Popconfirm } from '@douyinfe/semi-ui';
import { IconDelete } from '@douyinfe/semi-icons';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { useAuthStore } from '@/store/authStore';
import * as commentsApi from '@/lib/api/comments';
import type { Comment } from '@/lib/api/comments';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

interface CommentListProps {
  postId: string;
}

export default function CommentList({ postId }: CommentListProps) {
  const currentUser = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const { data: comments, isLoading } = useQuery(
    ['comments', postId],
    () => commentsApi.getComments(postId),
    { enabled: !!postId }
  );

  const deleteMutation = useMutation(
    (id: string) => commentsApi.deleteComment(id),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['comments', postId]);
        Toast.success('评论已删除');
      },
      onError: () => {
        Toast.error('删除失败');
      },
    }
  );

  if (isLoading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex gap-3">
            <Skeleton.Avatar />
            <div className="flex-1">
              <Skeleton.Title style={{ width: 80, height: 14, marginBottom: 8 }} />
              <Skeleton.Paragraph style={{ width: '100%' }} />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (!comments || comments.length === 0) {
    return (
      <Empty title="暂无评论" description="快来发表第一条评论吧" />
    );
  }

  return (
    <div className="space-y-4">
      {comments.map((comment: Comment) => (
        <div key={comment.id} className="flex gap-3">
          <Avatar
            size="small"
            src={comment.author?.avatar}
            alt={comment.author?.displayName || comment.author?.username}
          >
            {(comment.author?.displayName || comment.author?.username || 'U')[0]}
          </Avatar>
          <div className="flex-1">
            <div className="flex items-center mb-1">
              <span className="text-sm font-medium text-gray-900">
                {comment.author?.displayName || comment.author?.username}
              </span>
              <span className="text-gray-400 text-xs ml-2">
                {dayjs(comment.createdAt).fromNow()}
              </span>
              {currentUser && currentUser.id === comment.authorId && (
                <Popconfirm
                  title="确定要删除这条评论吗？"
                  onConfirm={() => deleteMutation.mutate(comment.id)}
                  position="bottomRight"
                >
                  <Button
                    icon={<IconDelete />}
                    theme="borderless"
                    size="small"
                    className="ml-auto"
                    style={{ color: '#94a3b8' }}
                  />
                </Popconfirm>
              )}
            </div>
            <p className="text-gray-700 text-sm">{comment.content}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
