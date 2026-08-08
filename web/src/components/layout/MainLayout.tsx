'use client';

import { ReactNode } from 'react';
import TopNav from './TopNav';
import Footer from './Footer';
import Sidebar from './Sidebar';

interface MainLayoutProps {
  children: ReactNode;
  showSidebar?: boolean;
}

export default function MainLayout({ children, showSidebar = true }: MainLayoutProps) {
  return (
    <div className="min-h-screen flex flex-col bg-canvas">
      <TopNav />

      <main className="flex-1 w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex gap-6">
          {/* 主内容区 */}
          <div className={`flex-1 max-w-4xl ${showSidebar ? '' : 'mx-auto'}`}>{children}</div>

          {/* 侧边栏 */}
          {showSidebar && (
            <div className="hidden lg:block w-72 flex-shrink-0">
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
