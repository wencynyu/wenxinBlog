'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  IconHome,
  IconStarStroked,
  IconLikeHeart,
  IconBook,
  IconSetting,
} from '@douyinfe/semi-icons';
import { Nav } from '@douyinfe/semi-ui';
import { useIsAdmin } from '@/hooks/useAuth';

interface NavItem {
  key: string;
  label: string;
  icon: React.ReactNode;
  path: string;
  adminOnly?: boolean;
}

const navItems: NavItem[] = [
  { key: 'home', label: '首页', icon: <IconHome />, path: '/' },
  { key: 'feed', label: '推荐', icon: <IconStarStroked />, path: '/feed' },
  { key: 'trending', label: '热门', icon: <IconLikeHeart />, path: '/trending' },
  { key: 'posts', label: '博文', icon: <IconBook />, path: '/posts' },
  { key: 'admin', label: '管理', icon: <IconSetting />, path: '/admin', adminOnly: true },
];

export default function Navbar() {
  const pathname = usePathname();
  const isAdmin = useIsAdmin();
  // 防 hydration mismatch：useIsAdmin 读 zustand token（persist），SSR 时为 false，客户端挂载后才真实
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const showAdmin = mounted && isAdmin;
  const activeKey =
    navItems.find(
      (item) => pathname === item.path || (item.path !== '/' && pathname.startsWith(item.path)),
    )?.key || 'home';
  const visibleItems = navItems.filter((item) => !item.adminOnly || showAdmin);

  return (
    <nav className="border-b border-gray-200 bg-white dark:bg-gray-800 dark:border-gray-700">
      <div className="container-custom">
        <Nav mode="horizontal" activeKey={activeKey} header={{ text: null }} footer={null}>
          {visibleItems.map((item) => (
            <Nav.Item
              key={item.key}
              itemKey={item.key}
              text={
                <Link href={item.path} className="flex items-center space-x-1">
                  {item.icon}
                  <span className="dark:text-gray-300">{item.label}</span>
                </Link>
              }
            />
          ))}
        </Nav>
      </div>
    </nav>
  );
}
