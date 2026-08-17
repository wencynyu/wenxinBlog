'use client';

import { useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { isManagedImageHost } from '@/lib/image';
import { Card, Tag } from '@douyinfe/semi-ui';
import { useAuthStore } from '@/store/authStore';
import { useAdSlot } from '@/hooks/useAds';
import { trackAdClick } from '@/lib/api/ads';
import { getAnonymousId } from '@/lib/anonId';

interface AdSlotProps {
  position: 'FEED' | 'SIDEBAR';
}

/** 广告位：调 /api/v1/ads/decision 取素材（当前为 house ads）并渲染。
 *  视觉与 RecommendationCard 对齐；空结果/失败渲染 null，广告永不影响页面布局。
 *  点击 fire-and-forget 上报后跳转（后端 24h 去重）。 */
export default function AdSlot({ position }: AdSlotProps) {
  // 匿名访客也带稳定标识：后端 userId=null 会绕过频控且去重 key 退化为共享 IP
  const userId = useAuthStore((s) => s.user?.id) || getAnonymousId();
  const { data: ad } = useAdSlot(position, userId);
  const [imgFailed, setImgFailed] = useState(false);

  if (!ad) return null;

  const handleClick = () => {
    trackAdClick(ad.creativeId, userId).catch(() => {});
  };

  const content = (
    <Card
      className="shadow-card hover:shadow-card-hover transition-shadow overflow-hidden"
      bodyStyle={{ padding: 0 }}
    >
      {ad.imageUrl && !imgFailed && (
        <div className="relative w-full aspect-[16/6] bg-canvas">
          <Image
            src={ad.imageUrl}
            alt={ad.title}
            fill
            unoptimized={!isManagedImageHost(ad.imageUrl)}
            sizes="(min-width: 1024px) 288px, 100vw"
            className="object-cover"
            onError={() => setImgFailed(true)}
          />
        </div>
      )}
      <div className="p-4 flex items-center gap-2">
        <Tag size="small" color="orange">
          推广
        </Tag>
        <span className="text-sm text-ink font-medium truncate">{ad.title}</span>
      </div>
    </Card>
  );

  if (ad.landingUrl.startsWith('/')) {
    return (
      <Link href={ad.landingUrl} onClick={handleClick} className="block no-underline">
        {content}
      </Link>
    );
  }
  return (
    <a
      href={ad.landingUrl}
      onClick={handleClick}
      target="_blank"
      rel="noopener noreferrer sponsored"
      className="block no-underline"
    >
      {content}
    </a>
  );
}
