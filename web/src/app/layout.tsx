import type { Metadata, Viewport } from 'next';
import QueryProvider from '@/components/provider/QueryProvider';
import AuthProvider from '@/components/provider/AuthProvider';
import ThemeProvider from '@/components/provider/ThemeProvider';
import { SITE_URL } from '@/lib/site';
// Semi 主题变量（--semi-color-* 定义在 body / body[theme-mode=dark] 上）。
// 分组件 CSS 只有结构规则不含变量定义，不引入此文件时
// SideSheet 面板/遮罩等所有 var() 颜色会失效为透明
import '@douyinfe/semi-ui/lib/es/_base/base.css';
import '@/styles/globals.css';

export const metadata: Metadata = {
  // metadataBase：OG/Twitter 卡片相对 URL 解析的基准（缺失时 Next 会告警）
  metadataBase: new URL(SITE_URL),
  // template：页面级 title（如博文详情 generateMetadata）自动拼接站名后缀
  title: {
    default: 'WenxinBlog - 博文平台',
    template: '%s | WenxinBlog',
  },
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
        {/* 阻塞式读取持久化主题，首帧即应用暗色，避免暗色用户刷新时先闪亮色（FOUC）。
            与 ThemeProvider 的 effect（html.dark + body[theme-mode]）保持同一套类名 */}
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var s=JSON.parse(localStorage.getItem('ui-storage'));if(s&&s.state&&s.state.theme==='dark'){document.documentElement.classList.add('dark');document.addEventListener('DOMContentLoaded',function(){document.body.setAttribute('theme-mode','dark');});}}catch(e){}})();`,
          }}
        />
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
