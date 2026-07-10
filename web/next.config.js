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
  },

  // Semi-Design 是 barrel 库，开启按需导入以显著缩小 bundle
  experimental: {
    optimizePackageImports: ['@douyinfe/semi-ui', '@douyinfe/semi-icons'],
  },

  // 图片域名配置
  images: {
    domains: ['localhost', 'cdn.wenxinblog.com'],
    remotePatterns: [
      {
        protocol: 'https',
        hostname: '**.wenxinblog.com',
      },
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
