'use client';

import Link from 'next/link';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { IconLikeHeart, IconComment } from '@douyinfe/semi-icons';
import { Avatar } from '@douyinfe/semi-ui';
import type { FeedRecommendation } from '@/lib/api/recommend';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

interface RecommendationCardProps {
  item: FeedRecommendation;
}

/** 推荐卡片：渲染 FeedRecommendation（来自 recommendation-service）。
 *  不复用 PostCard —— PostCard 依赖 isLiked/isFavorited + blog-service 点赞收藏 mutation，
 *  推荐结果没有这些字段；这里只展示 + 相似度 score 徽章。 */
export default function RecommendationCard({ item }: RecommendationCardProps) {
  return (
    <Link href={`/posts/${item.id}`} className="group block">
      <article className="bg-surface rounded-xl shadow-card hover:shadow-card-hover transition-all duration-200 overflow-hidden">
        <div className="p-5">
          {/* 作者信息 + 推荐分数 */}
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center">
              <Avatar
                size="small"
                src={item.author?.avatar}
                alt={item.author?.displayName || item.author?.username}
              >
                {(item.author?.displayName || item.author?.username || 'U')[0]}
              </Avatar>
              <div className="ml-2 flex items-center gap-2">
                <span className="text-sm font-medium text-ink">
                  {item.author?.displayName || item.author?.username}
                </span>
                <span className="text-ink-faint text-xs font-mono">
                  {dayjs(item.createdAt).fromNow()}
                </span>
              </div>
            </div>
            {(item.score ?? 0) > 0 && (
              <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-primary-50 text-primary-700">
                {((item.score ?? 0) * 100).toFixed(0)}% 匹配
              </span>
            )}
          </div>

          {/* 标题和摘要 */}
          <h3 className="text-lg font-semibold text-ink mb-2 line-clamp-2 group-hover:text-primary-700 transition-colors">
            {item.title}
          </h3>
          {item.summary && (
            <p className="text-ink-muted text-sm mb-3 line-clamp-2">{item.summary}</p>
          )}

          {/* 标签 */}
          {item.tags && item.tags.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-3">
              {item.tags.slice(0, 3).map((tag) => (
                <span
                  key={tag}
                  className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono bg-primary-50 text-primary-700"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}

          {/* 互动统计（只读） */}
          <div className="flex items-center text-ink-faint text-sm font-mono">
            <span className="flex items-center mr-4">
              <IconLikeHeart size="small" className="mr-1" />
              {item.likeCount || 0}
            </span>
            <span className="flex items-center">
              <IconComment size="small" className="mr-1" />
              {item.commentCount || 0}
            </span>
          </div>
        </div>
      </article>
    </Link>
  );
}
