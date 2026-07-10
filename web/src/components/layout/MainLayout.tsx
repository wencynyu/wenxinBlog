'use client';

import { ReactNode } from 'react';
import Header from './Header';
import Navbar from './Navbar';
import Footer from './Footer';
import Sidebar from './Sidebar';

interface MainLayoutProps {
  children: ReactNode;
  showSidebar?: boolean;
}

export default function MainLayout({ children, showSidebar = true }: MainLayoutProps) {
  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900">
      <Header />
      <Navbar />

      <main className="flex-1 container-custom py-6">
        <div className="flex gap-6">
          {/* 主内容区 */}
          <div className={`flex-1 ${showSidebar ? 'max-w-3xl' : 'max-w-3xl mx-auto'}`}>
            {children}
          </div>

          {/* 侧边栏 */}
          {showSidebar && (
            <div className="hidden lg:block w-80 flex-shrink-0">
              <div className="sticky top-24">
                <Sidebar />
              </div>
            </div>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
}
