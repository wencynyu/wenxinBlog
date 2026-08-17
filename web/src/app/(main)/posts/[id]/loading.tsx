import { Skeleton } from '@douyinfe/semi-ui';

/** 详情页 force-dynamic 服务端取数期间的路由级 loading，避免导航白屏等待 */
export default function PostDetailLoading() {
  return (
    <div className="max-w-3xl mx-auto py-8 px-4">
      <Skeleton.Title style={{ width: '40%', height: 28, marginBottom: 20 }} />
      <Skeleton.Paragraph rows={8} />
    </div>
  );
}
