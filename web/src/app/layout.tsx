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
  maximumScale: 1,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body>
        <QueryProvider>
          <AuthProvider>
            <ThemeProvider>
              {children}
            </ThemeProvider>
          </AuthProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
