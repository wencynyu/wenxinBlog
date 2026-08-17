const withBundleAnalyzer = require('@next/bundle-analyzer')({
  enabled: process.env.ANALYZE === 'true',
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,

  // 环境变量
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
    NEXT_PUBLIC_OSS_URL: process.env.NEXT_PUBLIC_OSS_URL || 'http://localhost:9000',
    // 站点绝对 URL（metadataBase / sitemap / robots / canonical 用）
    NEXT_PUBLIC_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000',
  },

  // Semi-Design 是 barrel 库，开启按需导入以显著缩小 bundle
  experimental: {
    optimizePackageImports: ['@douyinfe/semi-ui', '@douyinfe/semi-icons'],
  },

  // 图片优化器只放行自有域名（站点 CDN + 本地开发），避免 /_next/image 沦为
  // 对任意主机的开放代理。用户粘贴的外链封面在组件层以 unoptimized 直出（见 src/lib/image.ts）
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: '**.wenxinblog.com' },
      { protocol: 'http', hostname: 'localhost' },
      { protocol: 'https', hostname: 'localhost' },
    ],
  },

  // SEO配置
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          {
            key: 'X-DNS-Prefetch-Control',
            value: 'on',
          },
          {
            key: 'X-Frame-Options',
            value: 'SAMEORIGIN',
          },
        ],
      },
    ];
  },
};

module.exports = withBundleAnalyzer(nextConfig);
