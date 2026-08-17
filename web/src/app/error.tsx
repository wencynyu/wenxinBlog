'use client';

import { Button, Typography } from '@douyinfe/semi-ui';

const { Title, Text } = Typography;

/** 根级渲染错误边界：任何路由抛错都落在这里，替代 Next 默认白屏（保留导航可退出） */
export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center py-20 px-4">
      <Title heading={2} className="mb-3">
        页面出错了
      </Title>
      <Text type="tertiary" className="mb-8">
        渲染过程发生异常，请重试或稍后再来
      </Text>
      <div className="flex gap-3">
        <Button theme="solid" onClick={() => reset()}>
          重试
        </Button>
        <Button theme="borderless" onClick={() => (window.location.href = '/')}>
          回首页
        </Button>
      </div>
    </div>
  );
}
