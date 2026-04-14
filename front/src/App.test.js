import { render, screen } from '@testing-library/react';
import App from './App';

jest.mock('react-cookies', () => ({
  __esModule: true,
  default: {
    load: jest.fn(),
    save: jest.fn(),
    remove: jest.fn(),
  },
}));

jest.mock('fetch-http-client', () => {
  const mockRequestHandlers = {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  };
  const FetchHttpClient = jest.fn(() => ({
    addMiddleware: jest.fn(),
    get: mockRequestHandlers.get,
    post: mockRequestHandlers.post,
    put: mockRequestHandlers.put,
    delete: mockRequestHandlers.delete,
  }));

  return {
    __esModule: true,
    default: FetchHttpClient,
    json: jest.fn(() => 'json-middleware'),
    mockRequestHandlers,
  };
});

jest.mock('./Navigation', () => ({
  __esModule: true,
  default: () => <div>navigation shell</div>,
}));

jest.mock('./PageContent', () => ({
  __esModule: true,
  default: () => <div>page content shell</div>,
}));

jest.mock('./Login', () => ({
  __esModule: true,
  default: () => <div>login shell</div>,
}));

jest.mock('./Admin', () => ({
  __esModule: true,
  default: () => <div>admin shell</div>,
}));

jest.mock('./JavaSqlAdmin', () => ({
  __esModule: true,
  default: () => (
    <div>
      <div>navigation shell</div>
      <div>page content shell</div>
    </div>
  ),
}));

jest.mock('./sqlGuid', () => ({
  __esModule: true,
  default: () => <div>sql guid shell</div>,
}));

const mockCookie = require('react-cookies').default;
const { mockRequestHandlers } = require('fetch-http-client');

describe('App routes', () => {
  beforeEach(() => {
    mockCookie.load.mockReset();
    mockCookie.save.mockReset();
    mockCookie.remove.mockReset();
    window.history.pushState({}, '', '/');
  });

  test('renders the login route', async () => {
    mockCookie.load.mockReturnValue(undefined);
    window.history.pushState({}, '', '/login');

    render(<App />);

    expect(await screen.findByText('login shell')).toBeInTheDocument();
  });

  test('renders the main shell for a logged-in user', async () => {
    mockCookie.load.mockReturnValue('token-123');

    render(<App />);

    expect(screen.getByText('navigation shell')).toBeInTheDocument();
    expect(screen.getByText('page content shell')).toBeInTheDocument();
  });
});
