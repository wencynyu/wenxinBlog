'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import { Nav, Input, Dropdown, Avatar, Button, Toast } from '@douyinfe/semi-ui';
import {
  IconSearch,
  IconBell,
  IconUser,
  IconMoon,
  IconSun,
  IconSetting,
  IconExit,
  IconHome,
  IconStarStroked,
  IconLikeHeart,
  IconBook,
} from '@douyinfe/semi-icons';
import { useAuthStore } from '@/store/authStore';
import { useUIStore } from '@/store/uiStore';
import { useLogout, useIsAdmin } from '@/hooks/useAuth';
import BrandLogo from '@/components/common/BrandLogo';

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

/** 一体化顶栏：合并原 Header（Logo/搜索/用户）+ Navbar（主导航）为单行，全部基于 Semi 组件。 */
export default function TopNav() {
  const router = useRouter();
  const pathname = usePathname();
  const { user, isAuthenticated } = useAuthStore();
  const { theme, toggleTheme } = useUIStore();
  const logout = useLogout();
  const isAdmin = useIsAdmin();
  // 防 hydration mismatch：zustand persist 在 SSR 读不到 localStorage
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const authed = mounted && isAuthenticated;
  const isDarkMode = mounted && theme === 'dark';
  const showAdmin = mounted && isAdmin;

  // 顶部搜索框受控：搜索后清空，避免残留旧词（同词再搜 Next 不触发导航）
  const [search, setSearch] = useState('');
  const handleSearch = (value: string) => {
    const v = value.trim();
    if (v) {
      router.push(`/search?q=${encodeURIComponent(v)}`);
      setSearch('');
    }
  };

  const handleLogout = () => {
    logout.mutate();
    Toast.success('已退出登录');
  };

  const userMenu = (
    <Dropdown.Menu>
      <Dropdown.Item onClick={() => router.push(`/user/${user?.id}`)}>
        <IconUser className="mr-2" />
        我的主页
      </Dropdown.Item>
      <Dropdown.Item onClick={() => router.push('/settings')}>
        <IconSetting className="mr-2" />
        个人设置
      </Dropdown.Item>
      <Dropdown.Divider />
      <Dropdown.Item onClick={handleLogout}>
        <IconExit className="mr-2" />
        退出登录
      </Dropdown.Item>
    </Dropdown.Menu>
  );

  const visibleItems = navItems.filter((item) => !item.adminOnly || showAdmin);
  const activeKey =
    visibleItems.find(
      (item) => pathname === item.path || (item.path !== '/' && pathname.startsWith(item.path)),
    )?.key || 'home';

  return (
    <header className="sticky top-0 z-50 w-full border-b border-hairline bg-surface/95 backdrop-blur">
      <div className="container-custom flex h-16 items-center gap-4">
        <BrandLogo />

        <Nav
          mode="horizontal"
          activeKey={activeKey}
          header={{ text: null }}
          footer={null}
          className="flex-1"
        >
          {visibleItems.map((item) => (
            <Nav.Item
              key={item.key}
              itemKey={item.key}
              text={
                <Link href={item.path} className="flex items-center gap-1.5 no-underline">
                  {item.icon}
                  <span>{item.label}</span>
                </Link>
              }
            />
          ))}
        </Nav>

        <div className="hidden md:block w-56 flex-shrink-0">
          <Input
            value={search}
            onChange={(v) => setSearch(v)}
            placeholder="搜索博文..."
            prefix={<IconSearch />}
            onEnterPress={(e: any) => handleSearch(e.target.value)}
            showClear
          />
        </div>

        <div className="flex items-center gap-1">
          <Button
            icon={isDarkMode ? <IconSun /> : <IconMoon />}
            theme="borderless"
            onClick={toggleTheme}
          />
          {authed && <Button icon={<IconBell />} theme="borderless" />}

          {!mounted ? (
            <div className="w-20" />
          ) : authed && user ? (
            <Dropdown trigger="click" position="bottomRight" render={userMenu}>
              <button className="flex items-center gap-2 hover:bg-canvas rounded-full px-2 py-1 transition-colors">
                <Avatar size="small" src={user.avatar} alt={user.displayName || user.username}>
                  {(user.displayName || user.username || 'U')[0]}
                </Avatar>
                <span className="text-sm font-medium text-ink-muted hidden sm:block">
                  {user.displayName || user.username}
                </span>
              </button>
            </Dropdown>
          ) : (
            <div className="flex items-center gap-2">
              <Link href="/login">
                <Button theme="borderless">登录</Button>
              </Link>
              <Link href="/register">
                <Button theme="solid">注册</Button>
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
