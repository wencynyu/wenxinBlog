import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AdSlot from './AdSlot';
import type { AdDecision } from '@/lib/api/ads';

const { trackAdClick } = vi.hoisted(() => ({ trackAdClick: vi.fn() }));

// Semi Design 的 barrel import 会加载 lottie-web，其模块级 canvas 调用在 jsdom 下崩溃
vi.mock('lottie-web', () => ({}));

vi.mock('@/store/authStore', () => ({
  useAuthStore: (selector: (s: { user: { id: string } | null }) => unknown) =>
    selector({ user: null }),
}));

vi.mock('@/lib/anonId', () => ({
  getAnonymousId: () => 'anon-test-id',
}));

vi.mock('@/hooks/useAds', () => ({
  useAdSlot: (_position: string, _userId?: string) => ({ data: currentAd }),
}));

vi.mock('@/lib/api/ads', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api/ads')>();
  return { ...actual, trackAdClick };
});

const sampleAd: AdDecision = {
  creativeId: 1,
  campaignId: 100,
  title: '发现优质博文',
  imageUrl: 'https://example.com/banner.jpg',
  landingUrl: '/posts',
  creativeType: 'IMAGE',
  bidAmount: 5,
};

let currentAd: AdDecision | null | undefined;

describe('AdSlot', () => {
  beforeEach(() => {
    trackAdClick.mockClear();
    // 真实 trackAdClick 是 async，始终返回 Promise
    trackAdClick.mockResolvedValue(undefined);
    currentAd = undefined;
  });

  it('renders nothing when no ad is served', () => {
    const { container } = render(<AdSlot position="FEED" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders ad title and promoted badge', () => {
    currentAd = sampleAd;
    render(<AdSlot position="FEED" />);
    expect(screen.getByText('发现优质博文')).toBeInTheDocument();
    expect(screen.getByText('推广')).toBeInTheDocument();
  });

  it('reports click (fire-and-forget) when clicked', () => {
    currentAd = sampleAd;
    render(<AdSlot position="FEED" />);
    fireEvent.click(screen.getByText('发现优质博文'));
    expect(trackAdClick).toHaveBeenCalledWith(1, 'anon-test-id');
  });

  it('falls back to text-only when image fails to load', async () => {
    currentAd = { ...sampleAd, imageUrl: 'https://example.com/broken.jpg' };
    render(<AdSlot position="SIDEBAR" />);
    // 图片加载失败后标题仍在，卡片不整块消失
    expect(screen.getByText('发现优质博文')).toBeInTheDocument();
  });
});
