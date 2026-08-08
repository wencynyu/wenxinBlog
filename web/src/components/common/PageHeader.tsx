import { Typography } from '@douyinfe/semi-ui';

/** 统一页头：eyebrow（等宽小标签）+ 标题（Semi Typography.Title）+ 右侧操作区。
 *  收口 posts/feed/trending/search/settings/editor/详情页 重复手搓的页头。 */
export default function PageHeader({
  eyebrow,
  title,
  extra,
}: {
  eyebrow?: string;
  title: string;
  extra?: React.ReactNode;
}) {
  return (
    <div className="mb-8 flex items-end justify-between gap-4">
      <div>
        {eyebrow && <p className="eyebrow mb-2">{eyebrow}</p>}
        <Typography.Title heading={2} style={{ marginBottom: 0 }}>
          {title}
        </Typography.Title>
      </div>
      {extra && <div className="flex items-center gap-2">{extra}</div>}
    </div>
  );
}
