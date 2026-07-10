import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import AuthProvider from './AuthProvider';

const checkAuth = vi.fn();

vi.mock('@/store/authStore', () => ({
  useAuthStore: () => checkAuth,
}));

describe('AuthProvider', () => {
  it('invokes checkAuth once on mount', () => {
    render(
      <AuthProvider>
        <div>child</div>
      </AuthProvider>,
    );
    expect(checkAuth).toHaveBeenCalledTimes(1);
  });
});
