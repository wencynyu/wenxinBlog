'use client';

import { useQuery } from '@tanstack/react-query';
import { getAdDecision, type AdPositionType } from '@/lib/api/ads';

/** 广告位数据。每次决策请求都会计费（CPM 扣预算），因此：
 *  不重试、不后台刷新、卸载后缓存 5 分钟内复用（快速切页不重复计费）。 */
export function useAdSlot(positionType: AdPositionType, userId: string | undefined) {
  return useQuery({
    queryKey: ['ad', positionType, userId],
    queryFn: () => getAdDecision(positionType, userId),
    staleTime: Infinity,
    gcTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}
