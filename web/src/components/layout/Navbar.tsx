'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { IconHome, IconStarStroked, IconLikeHeart, IconBook } from '@douyinfe/semi-icons';
import { Nav } from '@douyinfe/semi-ui';

interface NavItem {
  key: string;
  label: string;
  icon: React.ReactNode;
  path: string;
}

const navItems: NavItem[] = [
  { key: 'home', label: '首页', icon: <IconHome />, path: '/' },
  { key: 'feed', label: '推荐', icon: <IconStarStroked />, path: '/feed' },
  { key: 'trending', label: '热门', icon: <IconLikeHeart />, path: '/trending' },
  { key: 'posts', label: '博文', icon: <IconBook />, path: '/posts' },
];

export default function Navbar() {
  const pathname = usePathname();
  const activeKey = navItems.find(item => pathname === item.path)?.key || 'home';

  return (
    <nav className="border-b border-gray-200 bg-white dark:bg-gray-800 dark:border-gray-700">
      <div className="container-custom">
        <Nav
          mode="horizontal"
          activeKey={activeKey}
          header={{
            text: null,
          }}
          footer={null}
        >
          {navItems.map(item => (
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
