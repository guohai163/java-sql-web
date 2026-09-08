const RENDER_MODE_STORAGE_KEY = 'jsw_query_render_mode';
const QUERY_ERROR_TITLE = 'SQL 执行失败';
const QUERY_ERROR_DEFAULT_DETAIL = '查询执行失败，请稍后重试';

export const LARGE_RESULT_SIZE = 2000;

export function readRenderModePreference() {
  return localStorage.getItem(RENDER_MODE_STORAGE_KEY) !== 'legacy';
}

export function saveRenderModePreference(useModernMode) {
  localStorage.setItem(RENDER_MODE_STORAGE_KEY, useModernMode ? 'modern' : 'legacy');
}

export function createPane(overrides = {}) {
  return {
    title: 'MainTab',
    closable: false,
    key: 'Tab0',
    server: '',
    serverName: '',
    serverType: '',
    database: '',
    sql: '',
    queryResult: [],
    dataAreaRefresh: [],
    queryError: false,
    queryErrorTitle: '',
    queryErrorDetail: '',
    dataDisplayStyle: readRenderModePreference(),
    dashboardData: null,
    dashboardLoading: false,
    dashboardError: '',
    dashboardUpdatedAt: '',
    dashboardFetched: false,
    contentTab: 'query',
    vannaQuestion: '',
    vannaLoading: false,
    vannaResult: null,
    vannaError: '',
    schemaTables: {},
    selectedSql: '',
    ...overrides,
  };
}

export function getPaneByKey(panes, key) {
  return panes.find((pane) => pane.key === key) || panes[0];
}

export function updatePaneByKey(panes, key, updater) {
  return panes.map((pane) =>
    pane.key === key ? updater({ ...pane }) : pane,
  );
}

export function clearPaneQueryFeedback(pane) {
  return {
    ...pane,
    queryError: false,
    queryErrorTitle: '',
    queryErrorDetail: '',
  };
}

export function findSeedPane(panes, selectServer, selectDatabase, activeKey) {
  const activePane = getPaneByKey(panes, activeKey);
  const normalizedServer = selectServer && selectServer !== '0' ? selectServer : '';
  const normalizedDatabase = selectDatabase || '';

  if (
    activePane
    && activePane.server === normalizedServer
    && activePane.database === normalizedDatabase
  ) {
    return activePane;
  }

  const exactMatchPane = [...panes]
    .reverse()
    .find((pane) => pane.server === normalizedServer && pane.database === normalizedDatabase);
  if (exactMatchPane) {
    return exactMatchPane;
  }

  const serverMatchPane = [...panes]
    .reverse()
    .find((pane) => pane.server === normalizedServer);
  if (serverMatchPane) {
    return serverMatchPane;
  }

  return [...panes].reverse().find((pane) => pane.server || pane.database) || activePane;
}

export function buildQueryErrorFeedback(message) {
  return {
    queryError: true,
    queryErrorTitle: QUERY_ERROR_TITLE,
    queryErrorDetail:
      typeof message === 'string' && message.trim() !== ''
        ? message.trim()
        : QUERY_ERROR_DEFAULT_DETAIL,
  };
}

export function readHistorySql(serverCode) {
  const cacheKey = `${serverCode}_history_sql`;
  const historyData = localStorage.getItem(cacheKey);

  return historyData === null ? [] : JSON.parse(historyData);
}

function escapeCsvValue(value) {
  if (value === null || value === undefined) {
    return '';
  }

  const normalizedValue = String(value).replace(/\r\n/g, '\n');
  if (/[",\n]/.test(normalizedValue)) {
    return `"${normalizedValue.replace(/"/g, '""')}"`;
  }
  return normalizedValue;
}

export function buildCsvContent(rows) {
  if (!rows || rows.length === 0) {
    return '';
  }

  const headers = Object.keys(rows[0]);
  const csvRows = [
    headers.map(escapeCsvValue).join(','),
    ...rows.map((row) =>
      headers.map((header) => escapeCsvValue(row[header])).join(','),
    ),
  ];

  return csvRows.join('\r\n');
}

export function createExportFileName(pane) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const database = pane.database || 'query';
  return `${database}-${timestamp}.csv`;
}

export function quotePostgresqlIdentifier(identifier) {
  return `"${String(identifier || '').replace(/"/g, '""')}"`;
}
