import client from './client';
import type { ApiResponse } from '@/types/common';

export type AdPositionType = 'FEED' | 'SIDEBAR' | 'BANNER';

export interface AdDecision {
  creativeId: number;
  campaignId: number;
  title: string;
  imageUrl?: string;
  landingUrl: string;
  creativeType: string;
  bidAmount: number;
}

/** 拉取一条广告决策。注意：后端在决策时即记 IMPRESSION 并扣预算，
 *  调用方必须避免重复触发（useAdSlot 已配置为每挂载只取一次）。 */
export async function getAdDecision(
  positionType: AdPositionType,
  userId?: string,
): Promise<AdDecision | null> {
  const response: ApiResponse<AdDecision[]> = await client.post('/api/v1/ads/decision', {
    positionType,
    userId: userId || undefined,
    count: 1,
  });
  return response.data?.[0] ?? null;
}

/** 点击上报（fire-and-forget，后端 24h 去重）。 */
export async function trackAdClick(creativeId: number, userId?: string): Promise<void> {
  await client.post('/api/v1/ads/click', {
    creativeId,
    userId: userId || undefined,
  });
}
