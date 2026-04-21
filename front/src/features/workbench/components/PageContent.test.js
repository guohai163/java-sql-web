import React from 'react';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import PageContent from '@/features/workbench/components/PageContent';

const mockPubsub = {
  handler: null,
};

const mockApi = {
  get: jest.fn(),
  post: jest.fn(),
};

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

jest.mock('pubsub-js', () => ({
  subscribe: jest.fn((topic, handler) => {
    mockPubsub.handler = handler;
    return 'mock-token';
  }),
  unsubscribe: jest.fn(),
}));

jest.mock('react-cookies', () => ({
  load: jest.fn(() => 'mock-user-token'),
}));

jest.mock('@/shared/api/apiClient', () => ({
  createClient: jest.fn(() => mockApi),
}));

jest.mock('react-codemirror2', () => {
  const React = require('react');
  return {
    Controlled: ({ editorDidMount, onBeforeChange, value }) => {
      const editor = React.useMemo(
        () => ({
          scrollTo: jest.fn(),
          setCursor: jest.fn(),
        }),
        [],
      );

      React.useEffect(() => {
        editorDidMount?.(editor);
      }, [editorDidMount, editor]);

      return (
        <textarea
          aria-label="sql-editor"
          onChange={(event) => onBeforeChange?.(editor, null, event.target.value)}
          value={value}
        />
      );
    },
  };
});

jest.mock('@/features/workbench/components/Spreadsheet', () => (props) => (
  <div data-testid={`spreadsheet-${props.dataId}`}>{JSON.stringify(props.data)}</div>
));

jest.mock('@/features/workbench/components/DataDisplayFast', () => (props) => (
  <div data-testid="legacy-grid">{JSON.stringify(props.data)}</div>
));

jest.mock('@/features/workbench/components/WorkbenchDashboard', () => () => (
  <div data-testid="workbench-dashboard">dashboard</div>
));

function buildServerInfoResponse(serverName = '测试实例') {
  return {
    jsonData: {
      status: true,
      data: {
        dbServerType: 'mysql',
        dbServerName: serverName,
      },
    },
  };
}

function buildTableColumnResponse() {
  return {
    jsonData: {
      status: true,
      data: {
        demo: ['id', 'name'],
      },
    },
  };
}

function buildDashboardResponse() {
  return {
    jsonData: {
      status: true,
      data: {
        sections: [],
      },
    },
  };
}

function buildQueryResponse(status, message, data) {
  return {
    jsonData: {
      status,
      message,
      data,
    },
  };
}

function getResponseByUrl(url) {
  if (url.startsWith('/database/serverinfo/')) {
    return Promise.resolve(buildServerInfoResponse());
  }
  if (url.startsWith('/database/tablecolumn/')) {
    return Promise.resolve(buildTableColumnResponse());
  }
  if (url.startsWith('/database/dashboard/')) {
    return Promise.resolve(buildDashboardResponse());
  }
  throw new Error(`Unhandled GET request in test: ${url}`);
}

async function publishDatabaseSelection(selectDatabase = 'demo', selectServer = '1') {
  await act(async () => {
    mockPubsub.handler?.('', {
      type: 'database',
      selectDatabase,
      selectServer,
    });
  });

  await waitFor(() => {
    const activePanel = getActiveWorkbenchPanel();
    expect(activePanel.querySelector('.workbench-serverinfo')).toHaveTextContent(`数据库: ${selectDatabase}`);
  });
}

function getActiveWorkbenchPanel() {
  return document.querySelector(
    '.workbench-tabs > .ant-tabs-content-holder > .ant-tabs-content > .ant-tabs-tabpane-active',
  );
}

async function fillSql(sql) {
  const activePanel = getActiveWorkbenchPanel();
  const editor = within(activePanel).getByLabelText('sql-editor');
  await act(async () => {
    await userEvent.clear(editor);
    await userEvent.type(editor, sql);
  });

  await waitFor(() => {
    expect(editor).toHaveValue(sql);
  });
}

async function executeSql() {
  const activePanel = getActiveWorkbenchPanel();
  const executeButton = within(activePanel).getByRole('button', { name: '执行 SQL' });
  await act(async () => {
    await userEvent.click(executeButton);
  });
}

describe('PageContent', () => {
  beforeEach(() => {
    mockPubsub.handler = null;
    mockApi.get.mockReset();
    mockApi.post.mockReset();
    mockApi.get.mockImplementation(getResponseByUrl);
    localStorage.clear();
    window.requestAnimationFrame = (callback) => callback();
    Modal.confirm.mockClear();
  });

  test('shows dialog and in-panel error view when query fails with backend message', async () => {
    mockApi.post
      .mockResolvedValueOnce(buildQueryResponse(true, '', [{ id: 1, name: 'alpha' }]))
      .mockResolvedValueOnce(
        buildQueryResponse(false, "对象名'notify_operate_failed_log'无效。", null),
      );

    render(<PageContent />);
    await publishDatabaseSelection();

    await fillSql('SELECT * FROM ok_table');
    await executeSql();

    await waitFor(() => {
      expect(within(getActiveWorkbenchPanel()).getByTestId('spreadsheet-Tab0')).toHaveTextContent('alpha');
    });

    await fillSql('SELECT * FROM notify_operate_failed_log');
    await executeSql();

    await waitFor(() => {
      expect(screen.getByText('SQL 执行失败')).toBeInTheDocument();
      expect(screen.getByText("对象名'notify_operate_failed_log'无效。")).toBeInTheDocument();
    });

    expect(Modal.confirm).toHaveBeenLastCalledWith(
      expect.objectContaining({
        title: 'SQL 执行失败',
        content: "对象名'notify_operate_failed_log'无效。",
      }),
    );
    expect(screen.queryByText('alpha')).not.toBeInTheDocument();
    expect(screen.queryByText('还没有查询结果')).not.toBeInTheDocument();
  });

  test('falls back to default query error detail when backend message is blank', async () => {
    mockApi.post.mockResolvedValueOnce(buildQueryResponse(false, '', null));

    render(<PageContent />);
    await publishDatabaseSelection();
    await fillSql('SELECT * FROM any_table');
    await executeSql();

    await waitFor(() => {
      expect(screen.getByText('SQL 执行失败')).toBeInTheDocument();
      expect(screen.getByText('查询执行失败，请稍后重试')).toBeInTheDocument();
    });

    expect(Modal.confirm).toHaveBeenLastCalledWith(
      expect.objectContaining({
        title: 'SQL 执行失败',
        content: '查询执行失败，请稍后重试',
      }),
    );
  });

  test('clears error view after a subsequent successful query', async () => {
    mockApi.post
      .mockResolvedValueOnce(buildQueryResponse(false, '对象名无效。', null))
      .mockResolvedValueOnce(buildQueryResponse(true, '', [{ id: 2, name: 'beta' }]));

    render(<PageContent />);
    await publishDatabaseSelection();

    await fillSql('SELECT * FROM broken_table');
    await executeSql();

    await waitFor(() => {
      expect(screen.getByText('SQL 执行失败')).toBeInTheDocument();
      expect(screen.getByText('对象名无效。')).toBeInTheDocument();
    });

    await fillSql('SELECT * FROM fixed_table');
    await executeSql();

    await waitFor(() => {
      expect(within(getActiveWorkbenchPanel()).getByTestId('spreadsheet-Tab0')).toHaveTextContent('beta');
    });

    expect(screen.queryByText('SQL 执行失败')).not.toBeInTheDocument();
    expect(screen.queryByText('对象名无效。')).not.toBeInTheDocument();
  });

  test('keeps query error isolated to the tab where the failure occurred', async () => {
    mockApi.post
      .mockResolvedValueOnce(buildQueryResponse(false, '第一标签页执行失败。', null))
      .mockResolvedValueOnce(buildQueryResponse(true, '', [{ id: 3, name: 'gamma' }]));

    const { container } = render(<PageContent />);
    await publishDatabaseSelection('demo', '1');
    await fillSql('SELECT * FROM broken_table');
    await executeSql();

    await waitFor(() => {
      expect(screen.getByText('第一标签页执行失败。')).toBeInTheDocument();
    });

    const addTabButton = container.querySelector('.ant-tabs-nav-add');
    expect(addTabButton).not.toBeNull();
    await userEvent.click(addTabButton);

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: 'Tab Tab2' })).toBeInTheDocument();
    });

    await publishDatabaseSelection('demo_second', '1');
    await fillSql('SELECT * FROM good_table');
    await executeSql();

    await waitFor(() => {
      expect(within(getActiveWorkbenchPanel()).getByTestId('spreadsheet-Tab2')).toHaveTextContent('gamma');
    });

    await userEvent.click(screen.getByRole('tab', { name: 'MainTab' }));

    await waitFor(() => {
      expect(screen.getByText('SQL 执行失败')).toBeInTheDocument();
      expect(screen.getByText('第一标签页执行失败。')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('tab', { name: 'Tab Tab2' }));

    await waitFor(() => {
      expect(within(getActiveWorkbenchPanel()).getByTestId('spreadsheet-Tab2')).toHaveTextContent('gamma');
    });

    const hiddenError = screen.queryByText('第一标签页执行失败。');
    if (hiddenError) {
      expect(hiddenError).not.toBeVisible();
    }
  });
});
