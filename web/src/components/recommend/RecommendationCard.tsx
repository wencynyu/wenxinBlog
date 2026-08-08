'use client';

import Link from 'next/link';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { Card, Avatar, Tag, Typography } from '@douyinfe/semi-ui';
import { IconLikeHeart, IconComment } from '@douyinfe/semi-icons';
import type { FeedRecommendation } from '@/lib/api/recommend';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

interface RecommendationCardProps {
  item: FeedRecommendation;
}

/** 推荐卡片：渲染 FeedRecommendation（来自 recommendation-service）。
 *  不复用 PostCard —— PostCard 依赖 isLiked/isFavorited + blog-service 点赞收藏 mutation，
 *  推荐结果没有这些字段；这里只展示 + 相似度 score 徽章。视觉与 PostCard 对齐（Semi Card 体系）。 */
export default function RecommendationCard({ item }: RecommendationCardProps) {
  return (
    <Link href={`/posts/${item.id}`} className="group block no-underline">
      <Card
        className="shadow-card hover:shadow-card-hover transition-shadow overflow-hidden"
        bodyStyle={{ padding: 20 }}
      >
        {/* 作者信息 + 推荐分数 */}
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Avatar
              size="small"
              src={item.author?.avatar}
              alt={item.author?.displayName || item.author?.username}
            >
              {(item.author?.displayName || item.author?.username || 'U')[0]}
            </Avatar>
            <span className="text-sm font-medium text-ink">
              {item.author?.displayName || item.author?.username}
            </span>
            <span className="text-ink-faint text-xs font-mono">
              {dayjs(item.createdAt).fromNow()}
            </span>
          </div>
          {(item.score ?? 0) > 0 && (
            <Tag size="small" color="blue">
              {((item.score ?? 0) * 100).toFixed(0)}% 匹配
            </Tag>
          )}
        </div>

        {/* 标题和摘要 */}
        <Typography.Title
          heading={4}
          ellipsis={{ rows: 2 }}
          className="group-hover:text-primary-700 transition-colors"
          style={{ marginBottom: 8 }}
        >
          {item.title}
        </Typography.Title>
        {item.summary && (
          <Typography.Paragraph type="tertiary" ellipsis={{ rows: 2 }} style={{ marginBottom: 12 }}>
            {item.summary}
          </Typography.Paragraph>
        )}

        {/* 标签 */}
        {item.tags && item.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mb-3">
            {item.tags.slice(0, 3).map((tag) => (
              <Tag key={tag} size="small" color="blue">
                {tag}
              </Tag>
            ))}
          </div>
        )}

        {/* 互动统计（只读） */}
        <div className="flex items-center gap-4 text-ink-faint text-sm font-mono">
          <span className="flex items-center gap-1">
            <IconLikeHeart size="small" />
            {item.likeCount || 0}
          </span>
          <span className="flex items-center gap-1">
            <IconComment size="small" />
            {item.commentCount || 0}
          </span>
        </div>
      </Card>
    </Link>
  );
}
