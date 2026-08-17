import Link from 'next/link';
import { IconGithubLogo, IconTwitter } from '@douyinfe/semi-icons';
import BrandLogo from '@/components/common/BrandLogo';

const footerLinks = {
  product: [
    { label: '首页', href: '/' },
    { label: '博文', href: '/posts' },
    { label: '热门', href: '/trending' },
  ],
  community: [
    { label: 'GitHub', href: 'https://github.com', external: true },
    { label: 'Twitter', href: 'https://twitter.com', external: true },
  ],
};

export default function Footer() {
  return (
    <footer className="bg-canvas border-t border-hairline mt-auto">
      <div className="container-custom py-12">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Brand */}
          <div className="space-y-4">
            <BrandLogo />
            <p className="text-ink-muted text-sm">
              基于 Next.js 14 和 Semi-Design 的现代化博文平台
            </p>
            <div className="flex space-x-4">
              <a
                href="https://github.com"
                target="_blank"
                rel="noopener noreferrer"
                className="text-ink-faint hover:text-ink-muted"
              >
                <IconGithubLogo size="large" />
              </a>
              <a
                href="https://twitter.com"
                target="_blank"
                rel="noopener noreferrer"
                className="text-ink-faint hover:text-ink-muted"
              >
                <IconTwitter size="large" />
              </a>
            </div>
          </div>

          {/* Links */}
          <div>
            <h3 className="font-semibold text-ink mb-4">产品</h3>
            <ul className="space-y-2">
              {footerLinks.product.map((link) => (
                <li key={link.href}>
                  <Link href={link.href} className="text-ink-muted hover:text-ink text-sm">
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="font-semibold text-ink mb-4">社区</h3>
            <ul className="space-y-2">
              {footerLinks.community.map((link) => (
                <li key={link.href}>
                  <a
                    href={link.href}
                    target={link.external ? '_blank' : undefined}
                    rel={link.external ? 'noopener noreferrer' : undefined}
                    className="text-ink-muted hover:text-ink text-sm"
                  >
                    {link.label}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Copyright */}
        <div className="border-t border-hairline mt-8 pt-8 text-center text-ink-faint text-sm">
          <p>&copy; {new Date().getFullYear()} WenxinBlog. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
