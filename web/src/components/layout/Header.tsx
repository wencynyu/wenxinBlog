'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  IconSearch,
  IconBell,
  IconUser,
  IconMoon,
  IconSun,
  IconSetting,
  IconExit,
} from '@douyinfe/semi-icons';
import { Button, Input, Dropdown, Avatar, Toast } from '@douyinfe/semi-ui';
import { useAuthStore } from '@/store/authStore';
import { useUIStore } from '@/store/uiStore';
import { useLogout } from '@/hooks/useAuth';

export default function Header() {
  const router = useRouter();
  const { user, isAuthenticated } = useAuthStore();
  const { theme, toggleTheme } = useUIStore();
  const logout = useLogout();
  // zustand persist 在 SSR 读不到 localStorage（user/isAuthenticated/theme 为初始值），
  // 客户端首次渲染读到 localStorage 值 → 与 SSR 不一致触发 hydration error。
  // mounted 标志：SSR 与客户端首次渲染都按"未挂载"渲染一致态，挂载后再渲染真实态。
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const authed = mounted && isAuthenticated;
  const isDarkMode = mounted && theme === 'dark';

  // 顶部搜索框受控：搜索后清空，避免残留旧词让用户误以为"失效"
  // （残留时再搜同词 router.push 到相同 URL，Next 不触发导航）。
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

  return (
    <header className="sticky top-0 z-50 w-full border-b border-hairline bg-surface/95 backdrop-blur">
      <div className="container-custom">
        <div className="flex h-16 items-center justify-between">
          {/* Logo */}
          <Link href="/" className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-primary-600 flex items-center justify-center">
              <span className="text-white font-bold text-lg">W</span>
            </div>
            <span className="font-bold text-xl text-ink">WenxinBlog</span>
          </Link>

          {/* 搜索框 */}
          <div className="hidden md:flex flex-1 max-w-md mx-8">
            <Input
              value={search}
              onChange={(v) => setSearch(v)}
              placeholder="搜索博文..."
              prefix={<IconSearch />}
              onEnterPress={(e: any) => handleSearch(e.target.value)}
              showClear
            />
          </div>

          {/* 右侧操作区 */}
          <div className="flex items-center space-x-4">
            {/* 主题切换 */}
            <Button
              icon={isDarkMode ? <IconSun /> : <IconMoon />}
              theme="borderless"
              onClick={toggleTheme}
            />

            {/* 通知 */}
            {authed && <Button icon={<IconBell />} theme="borderless" />}

            {/* 用户菜单：未挂载时出占位，保证 SSR/CSR 一致 */}
            {!mounted ? (
              <div className="w-24" />
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
              <div className="flex items-center space-x-2">
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
      </div>
    </header>
  );
}
