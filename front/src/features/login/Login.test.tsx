// @ts-nocheck
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Login from '@/features/login/Login';

const mockClient = {
  get: jest.fn(),
  post: jest.fn(),
};

jest.mock('react-cookies', () => ({
  __esModule: true,
  default: {
    save: jest.fn(),
  },
}));

jest.mock('@/shared/api/apiClient', () => ({
  createClient: jest.fn(() => mockClient),
}));

jest.mock('@github/webauthn-json', () => ({
  supported: jest.fn(() => false),
}));

jest.mock('qrcode.react', () => (props) => <div data-testid="qr-code">{props.value}</div>);

jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    Modal: {
      ...actual.Modal,
      confirm: jest.fn(),
    },
  };
});

function renderLogin(initialPath: string) {
  window.history.replaceState({}, '', initialPath);
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<Login />} />
      </Routes>
    </MemoryRouter>,
  );
}

const mockCookie = require('react-cookies').default;
const { createClient } = require('@/shared/api/apiClient');

describe('Login OIDC callback handling', () => {
  beforeEach(() => {
    createClient.mockClear();
    mockCookie.save.mockReset();
    mockClient.get.mockReset();
    mockClient.post.mockReset();
    mockClient.get.mockResolvedValue({ jsonData: { status: false, data: { enabled: false } } });
  });

  test('shows a friendly notice when oidc authorization is denied', async () => {
    renderLogin('/login?oidc_error=access_denied&oidc_error_description=%E7%94%A8%E6%88%B7%E5%B7%B2%E6%8B%92%E7%BB%9D%E4%BA%86%E6%8E%88%E6%9D%83%E8%AF%B7%E6%B1%82');

    const notices = await screen.findAllByText('用户已拒绝了授权请求。你可以重新发起 OIDC 登录或使用账号密码登录。');
    expect(notices.length).toBeGreaterThan(0);
    await waitFor(() => {
      expect(window.location.pathname).toBe('/login');
      expect(window.location.search).toBe('');
    });
  });

  test('keeps existing bind flow when oidc token requires otp binding', async () => {
    renderLogin('/login?oidc_token=oidc-token&auth_status=BINDING&auth_secret=secret-123&user_name=guohai');

    expect(await screen.findByText('绑定 OTP')).toBeInTheDocument();
    expect(screen.getByText('Secret: secret-123')).toBeInTheDocument();
    await waitFor(() => {
      expect(window.location.pathname).toBe('/login');
      expect(window.location.search).toBe('');
    });
  });

  test('keeps existing verify flow when oidc token requires otp verification', async () => {
    renderLogin('/login?oidc_token=oidc-token&auth_status=BIND');

    expect(await screen.findByText('验证 OTP')).toBeInTheDocument();
    await waitFor(() => {
      expect(window.location.pathname).toBe('/login');
      expect(window.location.search).toBe('');
    });
  });
});
