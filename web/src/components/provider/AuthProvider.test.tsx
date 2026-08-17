import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AuthProvider from './AuthProvider';

const checkAuth = vi.fn();

vi.mock('@/store/authStore', () => ({
  // 按 selector 语义 mock：AuthProvider 会同时取 checkAuth 与 user?.id
  useAuthStore: (selector: (s: unknown) => unknown) => selector({ checkAuth, user: null }),
}));

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('AuthProvider', () => {
  it('invokes checkAuth once on mount', () => {
    renderWithProviders(
      <AuthProvider>
        <div>child</div>
      </AuthProvider>,
    );
    expect(checkAuth).toHaveBeenCalledTimes(1);
  });
});
