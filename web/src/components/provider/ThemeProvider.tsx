'use client';

import { useEffect, ReactNode } from 'react';
import { LocaleProvider } from '@douyinfe/semi-ui';
import zh_CN from '@douyinfe/semi-ui/lib/es/locale/source/zh_CN';
import { useUIStore } from '@/store/uiStore';

interface ThemeProviderProps {
  children: ReactNode;
}

export default function ThemeProvider({ children }: ThemeProviderProps) {
  const theme = useUIStore((state) => state.theme);

  useEffect(() => {
    // 应用主题到 <html>（驱动 Tailwind dark: 变体）
    const root = document.documentElement;
    root.classList.remove('light', 'dark');
    root.classList.add(theme);
    // 同步 Semi 暗色：body[theme-mode=dark] 激活 Semi 内置暗色 palette（见 globals.css）
    document.body.setAttribute('theme-mode', theme);
  }, [theme]);

  // LocaleProvider 必须在 client 组件内（@douyinfe/semi-ui 无 'use client' 指令，
  // 放服务端 layout 会把 Semi 拉进 SSR bundle 触发 createContext 崩溃）
  return <LocaleProvider locale={zh_CN}>{children}</LocaleProvider>;
}
