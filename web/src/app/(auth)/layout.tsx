'use client';

import { ReactNode } from 'react';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div
      className="min-h-screen flex items-center justify-center bg-canvas"
      style={{
        backgroundImage:
          'radial-gradient(60% 120% at 50% 0%, rgba(0,119,250,0.10), transparent 55%)',
      }}
    >
      <div className="w-full max-w-md px-4">{children}</div>
    </div>
  );
}
