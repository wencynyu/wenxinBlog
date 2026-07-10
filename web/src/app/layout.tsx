import type { Metadata, Viewport } from 'next';
import QueryProvider from '@/components/provider/QueryProvider';
import AuthProvider from '@/components/provider/AuthProvider';
import ThemeProvider from '@/components/provider/ThemeProvider';
import '@/styles/globals.css';

export const metadata: Metadata = {
  title: 'WenxinBlog - 博文平台',
  description: '基于Next.js 14和Semi-Design的现代化博文平台',
  keywords: ['blog', '博文', 'Next.js', 'React'],
  authors: [{ name: 'WenxinBlog' }],
  openGraph: {
    title: 'WenxinBlog - 博文平台',
    description: '基于Next.js 14和Semi-Design的现代化博文平台',
    type: 'website',
    locale: 'zh_CN',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'WenxinBlog - 博文平台',
    description: '基于Next.js 14和Semi-Design的现代化博文平台',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
};

// 字体通过 <link> 运行时加载（display=swap，无 CLS），
// 不使用 next/font 以避免在受限网络下构建期拉取 Google Fonts 失败。
const FONT_HREF =
  'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600;8..60,700&display=swap';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link href={FONT_HREF} rel="stylesheet" />
      </head>
      <body>
        <QueryProvider>
          <AuthProvider>
            <ThemeProvider>{children}</ThemeProvider>
          </AuthProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
