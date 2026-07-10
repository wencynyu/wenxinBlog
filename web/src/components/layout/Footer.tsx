'use client';

import Link from 'next/link';
import { IconGithubLogo, IconTwitter } from '@douyinfe/semi-icons';

const footerLinks = {
  product: [
    { label: '首页', href: '/' },
    { label: '博文', href: '/posts' },
    { label: '热门', href: '/trending' },
  ],
  company: [
    { label: '关于我们', href: '/about' },
    { label: '联系方式', href: '/contact' },
    { label: '隐私政策', href: '/privacy' },
  ],
  community: [
    { label: 'GitHub', href: 'https://github.com', external: true },
    { label: 'Twitter', href: 'https://twitter.com', external: true },
  ],
};

export default function Footer() {
  return (
    <footer className="bg-gray-50 border-t border-gray-200 mt-auto dark:bg-gray-800 dark:border-gray-700">
      <div className="container-custom py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand */}
          <div className="space-y-4">
            <div className="flex items-center space-x-2">
              <div className="h-8 w-8 rounded-lg bg-sky-500 flex items-center justify-center">
                <span className="text-white font-bold text-lg">W</span>
              </div>
              <span className="font-bold text-xl text-gray-900 dark:text-white">WenxinBlog</span>
            </div>
            <p className="text-gray-500 dark:text-gray-400 text-sm">
              基于 Next.js 14 和 Semi-Design 的现代化博文平台
            </p>
            <div className="flex space-x-4">
              <a
                href="https://github.com"
                target="_blank"
                rel="noopener noreferrer"
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              >
                <IconGithubLogo size="large" />
              </a>
              <a
                href="https://twitter.com"
                target="_blank"
                rel="noopener noreferrer"
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              >
                <IconTwitter size="large" />
              </a>
            </div>
          </div>

          {/* Links */}
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white mb-4">产品</h3>
            <ul className="space-y-2">
              {footerLinks.product.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white text-sm"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white mb-4">公司</h3>
            <ul className="space-y-2">
              {footerLinks.company.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white text-sm"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white mb-4">社区</h3>
            <ul className="space-y-2">
              {footerLinks.community.map((link) => (
                <li key={link.href}>
                  <a
                    href={link.href}
                    target={link.external ? '_blank' : undefined}
                    rel={link.external ? 'noopener noreferrer' : undefined}
                    className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white text-sm"
                  >
                    {link.label}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Copyright */}
        <div className="border-t border-gray-200 dark:border-gray-700 mt-8 pt-8 text-center text-gray-400 text-sm">
          <p>&copy; {new Date().getFullYear()} WenxinBlog. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
