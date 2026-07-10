'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { IconSearch, IconBell, IconUser, IconMoon, IconSun, IconSetting, IconExit } from '@douyinfe/semi-icons';
import { Button, Input, Dropdown, Avatar, Toast } from '@douyinfe/semi-ui';
import { useAuthStore } from '@/store/authStore';
import { useUIStore } from '@/store/uiStore';
import { useLogout } from '@/hooks/useAuth';

export default function Header() {
  const router = useRouter();
  const { user, isAuthenticated } = useAuthStore();
  const { theme, toggleTheme } = useUIStore();
  const logout = useLogout();
  const isDarkMode = theme === 'dark';

  const handleSearch = (value: string) => {
    if (value.trim()) {
      router.push(`/search?q=${encodeURIComponent(value)}`);
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
    <header className="sticky top-0 z-50 w-full border-b border-gray-200 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/60 dark:bg-gray-900/95 dark:border-gray-700">
      <div className="container-custom">
        <div className="flex h-16 items-center justify-between">
          {/* Logo */}
          <Link href="/" className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-sky-500 flex items-center justify-center">
              <span className="text-white font-bold text-lg">W</span>
            </div>
            <span className="font-bold text-xl text-gray-900 dark:text-white">WenxinBlog</span>
          </Link>

          {/* 搜索框 */}
          <div className="hidden md:flex flex-1 max-w-md mx-8">
            <Input
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
            {isAuthenticated && (
              <Button icon={<IconBell />} theme="borderless" />
            )}

            {/* 用户菜单 */}
            {isAuthenticated && user ? (
              <Dropdown
                trigger="click"
                position="bottomRight"
                render={userMenu}
              >
                <button className="flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full px-2 py-1 transition-colors">
                  <Avatar size="small" src={user.avatar} alt={user.displayName || user.username}>
                    {(user.displayName || user.username || 'U')[0]}
                  </Avatar>
                  <span className="text-sm font-medium text-gray-700 dark:text-gray-300 hidden sm:block">
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
