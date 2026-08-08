'use client';

import { Empty, Button } from '@douyinfe/semi-ui';

interface EmptyStateProps {
  title?: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
}

/**
 * 统一的空状态 / 错误状态占位组件。
 * 用于替代生产代码中回退到 mock 数据的做法。
 */
export default function EmptyState({
  title = '暂无内容',
  description = '稍后再试试吧',
  actionText,
  onAction,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <Empty title={<span>{title}</span>} description={description} />
      {actionText && onAction && (
        <Button theme="solid" className="mt-4" onClick={onAction}>
          {actionText}
        </Button>
      )}
    </div>
  );
}
