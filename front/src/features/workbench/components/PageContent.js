import React, { useEffect, useRef, useState } from 'react';
import Pubsub from 'pubsub-js';
import cookie from 'react-cookies';
import { EditorSelection } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { Button, Empty, List, Modal, Result, Spin, Switch, Tabs } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import { createClient } from '@/shared/api/apiClient';
import dot from '@/features/workbench/assets/dot.gif';
import DataDisplayFast from '@/features/workbench/components/DataDisplayFast';
import SqlEditor from '@/features/workbench/components/SqlEditor';
import Spreadsheet from '@/features/workbench/components/Spreadsheet';
import WorkbenchDashboard from '@/features/workbench/components/WorkbenchDashboard';
import {
  getServerTypeLabel,
  isMysqlFamily,
  normalizeServerType,
} from '@/features/workbench/lib/serverType';
import '@/features/workbench/styles/PageContent.css';

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;
const RENDER_MODE_STORAGE_KEY = 'jsw_query_render_mode';
const LARGE_RESULT_SIZE = 2000;
const QUERY_ERROR_TITLE = 'SQL 执行失败';
const QUERY_ERROR_DEFAULT_DETAIL = '查询执行失败，请稍后重试';

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

function readRenderModePreference() {
  return localStorage.getItem(RENDER_MODE_STORAGE_KEY) !== 'legacy';
}

function saveRenderModePreference(useModernMode) {
  localStorage.setItem(RENDER_MODE_STORAGE_KEY, useModernMode ? 'modern' : 'legacy');
}

function createPane(overrides = {}) {
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
    contentTab: 'dashboard',
    schemaTables: {},
    selectedSql: '',
    ...overrides,
  };
}

function getPaneByKey(panes, key) {
  return panes.find((pane) => pane.key === key) || panes[0];
}

function updatePaneByKey(panes, key, updater) {
  return panes.map((pane) =>
    pane.key === key ? updater({ ...pane }) : pane,
  );
}

function clearPaneQueryFeedback(pane) {
  return {
    ...pane,
    queryError: false,
    queryErrorTitle: '',
    queryErrorDetail: '',
  };
}

function buildQueryErrorFeedback(message) {
  return {
    queryError: true,
    queryErrorTitle: QUERY_ERROR_TITLE,
    queryErrorDetail:
      typeof message === 'string' && message.trim() !== ''
        ? message.trim()
        : QUERY_ERROR_DEFAULT_DETAIL,
  };
}

function readHistorySql(serverCode) {
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

function buildCsvContent(rows) {
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

function createExportFileName(pane) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const database = pane.database || 'query';
  return `${database}-${timestamp}.csv`;
}

function quotePostgresqlIdentifier(identifier) {
  return `"${String(identifier || '').replace(/"/g, '""')}"`;
}

function PageContent() {
  const initialPanes = [createPane()];
  const [state, setState] = useState({
    selectServer: '0',
    selectDatabase: '',
    selectTable: '',
    tableColumns: [],
    spName: '',
    token: cookie.load('token'),
    queryLoading: false,
    historySql: [],
    editorServerType: 'mysql',
    editorSchemaTables: {},
    activeKey: initialPanes[0].key,
    panes: initialPanes,
    deskHeight: document.body.clientHeight - 460,
  });
  const clientRef = useRef(createClient());
  const newTabIndexRef = useRef(2);
  const stateRef = useRef(state);
  const editorRef = useRef(null);
  const editorInteractionRef = useRef({
    beforeSql: '',
    rearSql: '',
    selectedSql: '',
  });

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  const resetEditorViewport = () => {
    window.requestAnimationFrame(() => {
      if (!editorRef.current) {
        return;
      }
      editorRef.current.dispatch({
        selection: EditorSelection.cursor(0),
        effects: EditorView.scrollIntoView(0, { y: 'start' }),
      });
      if (editorRef.current.scrollDOM) {
        editorRef.current.scrollDOM.scrollTop = 0;
      }
      editorRef.current.focus();
    });
  };

  const resetEditorInteraction = () => {
    editorInteractionRef.current = {
      beforeSql: '',
      rearSql: '',
      selectedSql: '',
    };
  };

  useEffect(() => {
    const handleResize = () => {
      setState((previous) => ({
        ...previous,
        deskHeight: document.body.clientHeight - 460,
      }));
    };

    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  useEffect(() => {
    const token = Pubsub.subscribe('dataSelect', (message, data) => {
      void handleDataSelect(data);
    });

    return () => {
      Pubsub.unsubscribe(token);
    };
  }, []);

  const formatDashboardUpdatedAt = () =>
    new Date().toLocaleString('zh-CN', {
      hour12: false,
    });

  const clearPaneDashboard = (pane) => ({
    ...pane,
    dashboardData: null,
    dashboardLoading: false,
    dashboardError: '',
    dashboardUpdatedAt: '',
    contentTab: pane.queryResult.length > 0 ? pane.contentTab : 'dashboard',
  });

  const loadDashboardForPane = async ({
    server,
    database,
    paneKey,
    forceRefresh = false,
  }) => {
    const current = stateRef.current;

    if (!server || !database) {
      setState((previous) => ({
        ...previous,
        panes: updatePaneByKey(previous.panes, paneKey, (pane) => clearPaneDashboard(pane)),
      }));
      return;
    }

    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (pane) => ({
        ...pane,
        dashboardLoading: true,
        dashboardError: '',
        contentTab: pane.queryResult.length === 0 ? 'dashboard' : pane.contentTab,
      })),
    }));

    const response = await clientRef.current.get(
      `/database/dashboard/${server}/${database}?forceRefresh=${forceRefresh ? 'true' : 'false'}`,
      { headers: { 'User-Token': current.token } },
    );

    if (response.jsonData.status) {
      setState((previous) => ({
        ...previous,
        panes: updatePaneByKey(previous.panes, paneKey, (pane) => ({
          ...pane,
          dashboardLoading: false,
          dashboardError: '',
          dashboardUpdatedAt: formatDashboardUpdatedAt(),
          dashboardData: {
            ...(response.jsonData.data || {}),
            updatedAt: formatDashboardUpdatedAt(),
          },
        })),
      }));
      return;
    }

    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (pane) => ({
        ...pane,
        dashboardLoading: false,
        dashboardError: response.jsonData.message || 'dashboard 加载失败',
        dashboardData: null,
      })),
    }));
  };

  const handleDataSelect = async (data) => {
    const current = stateRef.current;

    if (data.type === 'table') {
      const response = await clientRef.current.get(
        `/database/serverinfo/${data.selectServer}`,
        { headers: { 'User-Token': current.token } },
      );
      const serverType = normalizeServerType(response.jsonData.data.dbServerType);
      let sql = '';

      if (serverType === 'mssql') {
        sql = `SELECT top 100 * FROM [${data.selectTable}]`;
      } else if (isMysqlFamily(serverType) || serverType === 'clickhouse') {
        sql = `SELECT * FROM \`${data.selectDatabase}\`.\`${data.selectTable}\` limit 100`;
      } else if (serverType === 'postgresql') {
        sql = `SELECT * FROM public.${quotePostgresqlIdentifier(data.selectTable)} LIMIT 100`;
      }

      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: data.selectDatabase,
        selectTable: data.selectTable,
        editorServerType: serverType,
        historySql: readHistorySql(data.selectServer),
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          sql,
          server: data.selectServer,
          serverName: response.jsonData.data.dbServerName,
          serverType,
          database: data.selectDatabase,
        })),
      }));
      resetEditorInteraction();
      resetEditorViewport();
      return;
    }

    if (data.type === 'tableName') {
      setState((previous) => {
        const sql = `${editorInteractionRef.current.beforeSql} ${data.selectTable} ${editorInteractionRef.current.rearSql}`;
        return {
          ...previous,
          panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
            ...pane,
            sql,
          })),
        };
      });
      resetEditorViewport();
      return;
    }

    if (data.type === 'column') {
      setState((previous) => {
        const sql = `${editorInteractionRef.current.beforeSql} ${data.selectColumn} ${editorInteractionRef.current.rearSql}`;
        return {
          ...previous,
          panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
            ...pane,
            sql,
          })),
        };
      });
      resetEditorViewport();
      return;
    }

    if (data.type === 'sp') {
      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: data.selectDatabase,
        spName: data.spName,
      }));

      const response = await clientRef.current.get(
        `/database/storedprocedures/${data.selectServer}/${data.selectDatabase}/${data.spName}`,
        { headers: { 'User-Token': current.token } },
      );

      setState((previous) => ({
        ...previous,
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          sql: response.jsonData.data.procedureData,
          server: data.selectServer,
          database: data.selectDatabase,
        })),
      }));
      resetEditorInteraction();
      resetEditorViewport();
      return;
    }

    if (data.type === 'view') {
      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: data.selectDatabase,
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          server: data.selectServer,
          database: data.selectDatabase,
        })),
      }));

      const response = await clientRef.current.get(
        `/database/views/${data.selectServer}/${data.selectDatabase}/${data.viewName}`,
        { headers: { 'User-Token': current.token } },
      );

      setState((previous) => ({
        ...previous,
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          sql: response.jsonData.data.viewData,
        })),
      }));
      resetEditorInteraction();
      resetEditorViewport();
      return;
    }

    if (data.type === 'database') {
      const [serverResponse, tableColumnResponse] = await Promise.all([
        clientRef.current.get(`/database/serverinfo/${data.selectServer}`, {
          headers: { 'User-Token': current.token },
        }),
        clientRef.current.get(
          `/database/tablecolumn/${data.selectServer}/${data.selectDatabase}`,
          { headers: { 'User-Token': current.token } },
        ),
      ]);
      const serverType = normalizeServerType(serverResponse.jsonData.data.dbServerType);

      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: data.selectDatabase,
        editorServerType: serverType,
        editorSchemaTables:
          tableColumnResponse.jsonData.status
          && tableColumnResponse.jsonData.data
          && typeof tableColumnResponse.jsonData.data === 'object'
          && !Array.isArray(tableColumnResponse.jsonData.data)
            ? tableColumnResponse.jsonData.data
            : {},
        historySql: readHistorySql(data.selectServer),
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          server: data.selectServer,
          database: data.selectDatabase,
          serverName: serverResponse.jsonData.data.dbServerName,
          serverType,
          schemaTables:
            tableColumnResponse.jsonData.status
            && tableColumnResponse.jsonData.data
            && typeof tableColumnResponse.jsonData.data === 'object'
            && !Array.isArray(tableColumnResponse.jsonData.data)
              ? tableColumnResponse.jsonData.data
              : {},
          dashboardData: null,
          dashboardLoading: false,
          dashboardError: '',
          dashboardUpdatedAt: '',
          contentTab: 'dashboard',
        })),
      }));
      resetEditorInteraction();
      void loadDashboardForPane({
        server: data.selectServer,
        database: data.selectDatabase,
        paneKey: current.activeKey,
      });
      return;
    }

    if (data.type === 'server') {
      const response = await clientRef.current.get(
        `/database/serverinfo/${data.selectServer}`,
        { headers: { 'User-Token': current.token } },
      );
      const serverType = normalizeServerType(response.jsonData.data.dbServerType);

      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: '',
        editorServerType: serverType,
        editorSchemaTables: {},
        historySql: readHistorySql(data.selectServer),
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...clearPaneDashboard(pane),
          server: data.selectServer,
          serverName: response.jsonData.data.dbServerName,
          serverType,
          database: '',
          schemaTables: {},
        })),
      }));
      resetEditorInteraction();
    }
  };

  const saveCursorValue = (snapshot) => {
    if (!snapshot) {
      return;
    }
    editorInteractionRef.current = {
      beforeSql: snapshot.beforeSql || '',
      rearSql: snapshot.rearSql || '',
      selectedSql: snapshot.selectedSql || '',
    };
  };

  const executeSql = async () => {
    const current = stateRef.current;
    const currentPane = getPaneByKey(current.panes, current.activeKey);
    const selectedSql = editorInteractionRef.current.selectedSql;
    const sql = selectedSql === '' ? currentPane.sql : selectedSql;

    if (sql === '') {
      showDialog('请输入SQL语句后再执行');
      return;
    }

    if (currentPane.database === '') {
      showDialog('请选择数据库后再执行');
      return;
    }

    setState((previous) => ({
      ...previous,
      queryLoading: true,
      panes: updatePaneByKey(previous.panes, currentPane.key, (pane) => ({
        ...clearPaneQueryFeedback(pane),
        queryResult: [],
        dataAreaRefresh: [],
      })),
    }));

    const response = await clientRef.current.post(
      `/database/query/${currentPane.server}/${currentPane.database}`,
      {
        headers: {
          'Content-Type': 'text/plain',
          'User-Token': current.token,
        },
        body: sql,
      },
    );

    if (response.jsonData.status) {
      if (response.jsonData.data.length === 0) {
        showDialog('无符合查询条件数据');
      }

      if (response.jsonData.message !== '') {
        showDialog(response.jsonData.message);
      }

      setState((previous) => {
        const currentHistory = previous.historySql.slice();
        const nextHistory = currentHistory.includes(sql)
          ? currentHistory
          : [sql, ...currentHistory];
        const preferredDisplayStyle = readRenderModePreference();
        const shouldUseModernDisplay = response.jsonData.data.length <= LARGE_RESULT_SIZE
          ? preferredDisplayStyle
          : false;

        if (!currentHistory.includes(sql)) {
          localStorage.setItem(
            `${previous.selectServer}_history_sql`,
            JSON.stringify(nextHistory),
          );
        }

        return {
          ...previous,
          queryLoading: false,
          historySql: nextHistory,
          panes: updatePaneByKey(previous.panes, currentPane.key, (pane) => ({
            ...clearPaneQueryFeedback(pane),
            contentTab: 'query',
            dataDisplayStyle: shouldUseModernDisplay,
            queryResult: response.jsonData.data,
            dataAreaRefresh: [sql],
          })),
        };
      });
      return;
    }

    const queryError = buildQueryErrorFeedback(response.jsonData.message);
    setState((previous) => ({
      ...previous,
      queryLoading: false,
      panes: updatePaneByKey(previous.panes, currentPane.key, (pane) => ({
        ...clearPaneQueryFeedback(pane),
        queryResult: [],
        dataAreaRefresh: [],
        contentTab: 'query',
        ...queryError,
      })),
    }));
    showDialog(queryError.queryErrorDetail, queryError.queryErrorTitle);
  };

  const historySqlToText = (sqlScript) => {
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
        ...pane,
        sql: sqlScript,
      })),
    }));
    resetEditorInteraction();
    resetEditorViewport();
  };

  const deleteHistorySql = (sqlScript) => {
    setState((previous) => {
      const nextHistorySql = previous.historySql.filter((item) => item !== sqlScript);
      localStorage.setItem(
        `${previous.selectServer}_history_sql`,
        JSON.stringify(nextHistorySql),
      );

      return {
        ...previous,
        historySql: nextHistorySql,
      };
    });
  };

  const dataStyleSwitch = (checked) => {
    saveRenderModePreference(checked);
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
        ...pane,
        dataDisplayStyle: checked,
      })),
    }));
  };

  const exportQueryResult = (pane) => {
    if (!pane.queryResult || pane.queryResult.length === 0) {
      showDialog('当前没有可导出的查询结果');
      return;
    }

    const csvContent = buildCsvContent(pane.queryResult);
    const csvBlob = new Blob([`\uFEFF${csvContent}`], {
      type: 'text/csv;charset=utf-8;',
    });
    const downloadUrl = URL.createObjectURL(csvBlob);
    const link = document.createElement('a');

    link.href = downloadUrl;
    link.download = createExportFileName(pane);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(downloadUrl);
  };

  const onTabsChange = (activeKey) => {
    setState((previous) => {
      const pane = getPaneByKey(previous.panes, activeKey);

      return {
        ...previous,
        activeKey,
        selectServer: pane.server || previous.selectServer,
        selectDatabase: pane.database || '',
        editorServerType: pane.serverType || 'mysql',
        editorSchemaTables: pane.schemaTables || {},
        historySql: pane.server ? readHistorySql(pane.server) : [],
      };
    });
    resetEditorInteraction();
  };

  const handleContentTabChange = (paneKey, contentTab) => {
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (pane) => ({
        ...pane,
        contentTab,
      })),
    }));
  };

  const addTab = () => {
    const activeKey = `Tab${newTabIndexRef.current++}`;

    setState((previous) => ({
      ...previous,
      activeKey,
      panes: [
        ...previous.panes,
        createPane({
          title: `Tab ${activeKey}`,
          key: activeKey,
          closable: true,
        }),
      ],
    }));
    resetEditorInteraction();
  };

  const removeTab = (targetKey) => {
    setState((previous) => {
      const panes = previous.panes.filter((pane) => pane.key !== targetKey);
      const nextActiveKey =
        previous.activeKey !== targetKey
          ? previous.activeKey
          : panes[Math.max(0, previous.panes.findIndex((pane) => pane.key === targetKey) - 1)]
              ?.key || panes[0]?.key;
      const nextPane = getPaneByKey(panes, nextActiveKey);

      return {
        ...previous,
        activeKey: nextActiveKey,
        panes,
        selectServer: nextPane?.server || '0',
        selectDatabase: nextPane?.database || '',
        historySql: nextPane?.server ? readHistorySql(nextPane.server) : [],
      };
    });
    resetEditorInteraction();
  };

  const handleTabsEdit = (targetKey, action) => {
    if (action === 'add') {
      addTab();
      return;
    }

    removeTab(targetKey);
  };

  return (
    <div className="right_area workbench-main-area">
      <Tabs
        className="workbench-tabs"
        activeKey={state.activeKey}
        onChange={onTabsChange}
        onEdit={handleTabsEdit}
        type="editable-card"
        items={state.panes.map((pane) => ({
          key: pane.key,
          label: pane.title,
          closable: pane.closable !== false,
          children: (
            <>
                <div id="menubar">
                  <div id="serverinfo" className="workbench-serverinfo">
                    <img src={dot} alt="SERVERIMG" className="icon ic_s_host " />
                  服务器: {pane.serverName} ({getServerTypeLabel(pane.serverType)})
                  <span className={pane.database === '' ? 'hide' : 'none'}>
                    &gt;&gt;{' '}
                    <img src={dot} className="icon ic_s_db " alt="DBIMG" />
                    数据库: {pane.database}
                  </span>
                </div>
              </div>
              <div className="page_content workbench-page-content">
                <div id="queryboxContainer" className="workbench-query-shell">
                  <fieldset id="queryboxf" className="workbench-query-card">
                    <div id="queryfieldscontainer" className="workbench-query-grid">
                      <div id="sqlquerycontainer" className="workbench-editor-panel">
                        <div className="workbench-panel-heading">
                          <div>
                            <h3>SQL 编辑器</h3>
                            <p>支持对象补全、选中执行和快速切换库表</p>
                          </div>
                        </div>
                        <SqlEditor
                          onChange={(value, snapshot) => {
                            saveCursorValue(snapshot);
                            setState((previous) => ({
                              ...previous,
                              panes: updatePaneByKey(previous.panes, pane.key, (currentPane) => ({
                                ...currentPane,
                                sql: value,
                              })),
                            }));
                          }}
                          onMount={(editorView) => {
                            editorRef.current = editorView;
                            if (editorView.scrollDOM) {
                              editorView.scrollDOM.scrollTop = 0;
                            }
                          }}
                          onSelectionChange={(snapshot) => {
                            saveCursorValue(snapshot);
                          }}
                          schemaTables={state.editorSchemaTables}
                          serverType={state.editorServerType}
                          value={pane.sql}
                        />
                        <div className="workbench-editor-tip">
                          敲入关键字首字母后可使用 Ctrl+Space 快速补全，选中部分 SQL 时只执行选中语句。
                        </div>
                      </div>
                      <div id="tablefieldscontainer" className="workbench-history-panel">
                        <div className="workbench-panel-heading">
                          <div>
                            <h3>历史记录</h3>
                            <p>保留最近执行的 SQL，方便回看和复用</p>
                          </div>
                        </div>
                        <List
                          dataSource={state.historySql}
                          renderItem={(item) => (
                            <List.Item className="workbench-history-item" key={item}>
                              <a className="workbench-history-link" onClick={() => historySqlToText(item)}>
                                {item.length > 60 ? `${item.substring(0, 60)}...` : item}
                              </a>
                              <button
                                className="workbench-history-delete"
                                onClick={() => deleteHistorySql(item)}
                              >
                                删除
                              </button>
                            </List.Item>
                          )}
                        />
                      </div>
                      <div className="clearfloat"></div>
                    </div>
                  </fieldset>
                </div>
                <fieldset id="queryboxfooter" className="tblFooters workbench-query-toolbar">
                  <div className="workbench-query-toolbar-meta">
                    <span className="workbench-render-label">结果视图</span>
                    <Switch
                      checked={pane.dataDisplayStyle}
                      checkedChildren="新版"
                      unCheckedChildren="旧版"
                      onChange={dataStyleSwitch}
                    />
                  </div>
                  <div className="workbench-query-toolbar-actions">
                    <Button id="button_submit_query" onClick={executeSql} type="primary">
                      执行 SQL
                    </Button>
                    {pane.queryResult.length !== 0 ? (
                      <Button onClick={() => exportQueryResult(pane)}>导出查询结果</Button>
                    ) : null}
                  </div>
                </fieldset>
                <div className="workbench-lower-panel">
                  <Tabs
                    activeKey={pane.contentTab}
                    className="workbench-content-tabs"
                    items={[
                      {
                        key: 'dashboard',
                        label: 'Dashboard',
                        children: (
                          <WorkbenchDashboard
                            data={pane.dashboardData}
                            error={pane.dashboardError}
                            loading={pane.dashboardLoading}
                            onRefresh={() =>
                              void loadDashboardForPane({
                                server: pane.server,
                                database: pane.database,
                                paneKey: pane.key,
                                forceRefresh: true,
                              })
                            }
                          />
                        ),
                      },
                      {
                        key: 'query',
                        label: '查询结果',
                        children: state.queryLoading ? (
                          <div className="query_load workbench-loading">
                            <Spin indicator={antIcon} />
                            数据查询中...
                          </div>
                        ) : pane.queryError ? (
                          <div className="query_load workbench-empty-state workbench-query-error-state">
                            <Result
                              status="error"
                              subTitle={pane.queryErrorDetail}
                              title={pane.queryErrorTitle}
                            />
                          </div>
                        ) : pane.queryResult.length === 0 ? (
                          <div className="query_load workbench-empty-state">
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有查询结果" />
                          </div>
                        ) : (
                          <div className="responsivetable workbench-result-panel">
                            <div className="workbench-panel-heading compact">
                              <div>
                                <h3>查询结果</h3>
                                <p>{pane.queryResult.length} 行数据</p>
                              </div>
                            </div>
                            <div className="container-wrap workbench-result-wrap" style={{ height: state.deskHeight }}>
                              {pane.dataDisplayStyle ? (
                                <Spreadsheet
                                  data={pane.queryResult}
                                  dataAreaRefresh={pane.dataAreaRefresh}
                                  dataId={pane.key}
                                />
                              ) : (
                                <DataDisplayFast
                                  data={pane.queryResult}
                                  dataAreaRefresh={pane.dataAreaRefresh}
                                />
                              )}
                            </div>
                          </div>
                        ),
                      },
                    ]}
                    onChange={(contentTab) => handleContentTabChange(pane.key, contentTab)}
                  />
                </div>
              </div>
            </>
          ),
        }))}
      />
    </div>
  );
}

export default PageContent;
