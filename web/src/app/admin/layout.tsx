'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useIsAdmin } from '@/hooks/useAuth';

const navItems = [
  { key: 'roles', label: '角色管理', href: '/admin/roles' },
  { key: 'permissions', label: '权限管理', href: '/admin/permissions' },
  { key: 'users', label: '用户管理', href: '/admin/users' },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const isAdmin = useIsAdmin();
  const pathname = usePathname();

  if (!isAdmin) {
    return (
      <div className="container-custom py-20 text-center">
        <h2 className="text-xl font-semibold mb-2">无权访问</h2>
        <p className="text-gray-500">该页面仅管理员可见。</p>
        <Link href="/" className="text-blue-600 hover:underline mt-4 inline-block">
          返回首页
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200">
        <div className="container-custom flex items-center h-14">
          <Link href="/" className="text-gray-600 hover:text-gray-900 text-sm mr-6">
            ← 返回主站
          </Link>
          <span className="font-semibold">管理后台</span>
        </div>
      </header>
      <div className="container-custom py-6 flex gap-6">
        <aside className="w-48 shrink-0">
          <nav className="space-y-1">
            {navItems.map((item) => {
              const active = pathname.startsWith(item.href);
              return (
                <Link
                  key={item.key}
                  href={item.href}
                  className={`block px-3 py-2 rounded-md text-sm ${
                    active
                      ? 'bg-blue-50 text-blue-600 font-medium'
                      : 'text-gray-700 hover:bg-gray-100'
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </aside>
        <main className="flex-1 min-w-0">{children}</main>
      </div>
    </div>
  );
}
