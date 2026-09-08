import React, { useEffect, useRef, useState } from 'react';
import Pubsub from 'pubsub-js';
import cookie from 'react-cookies';
import copy from 'copy-to-clipboard';
import { Modal, Tabs } from 'antd';
import { createClient } from '@/shared/api/apiClient';
import WorkbenchPane from '@/features/workbench/components/WorkbenchPane';
import type { SelectionSnapshot, SqlEditorHandle } from '@/features/workbench/components/SqlEditor';
import {
  LARGE_RESULT_SIZE,
  buildCsvContent,
  buildQueryErrorFeedback,
  clearPaneQueryFeedback,
  createExportFileName,
  createPane,
  findSeedPane,
  getPaneByKey,
  quotePostgresqlIdentifier,
  readHistorySql,
  readRenderModePreference,
  saveRenderModePreference,
  updatePaneByKey,
} from '@/features/workbench/lib/pageContentModel';
import {
  isMysqlFamily,
  normalizeServerType,
} from '@/features/workbench/lib/serverType';
import '@/features/workbench/styles/PageContent.css';

const { confirm } = Modal;

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

interface PageContentState {
  selectServer: string;
  selectDatabase: string;
  selectTable: string;
  tableColumns: any[];
  spName: string;
  token: string | undefined;
  queryLoading: boolean;
  historySql: string[];
  editorServerType: string;
  editorSchemaTables: Record<string, any>;
  activeKey: string;
  panes: any[];
  deskHeight: number;
}

function PageContent() {
  const initialPanes = [createPane()];
  const [state, setState] = useState<PageContentState>({
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
  const editorRef = useRef<SqlEditorHandle | null>(null);
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
      editorRef.current?.resetViewport();
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
    dashboardFetched: false,
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
          dashboardFetched: true,
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
        dashboardFetched: true,
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
          dashboardFetched: false,
          contentTab: 'query',
        })),
      }));
      resetEditorInteraction();
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
    const current = stateRef.current;
    const pane = getPaneByKey(current.panes, paneKey);

    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
        ...currentPane,
        contentTab,
      })),
    }));

    if (
      contentTab === 'dashboard'
      && pane.server
      && pane.database
      && !pane.dashboardLoading
      && !pane.dashboardFetched
    ) {
      void loadDashboardForPane({
        server: pane.server,
        database: pane.database,
        paneKey,
      });
    }
  };

  const addTab = () => {
    const activeKey = `Tab${newTabIndexRef.current++}`;

    setState((previous) => {
      const nextServer =
        previous.selectServer && previous.selectServer !== '0' ? previous.selectServer : '';
      const nextDatabase = previous.selectDatabase || '';
      const seedPane = findSeedPane(
        previous.panes,
        previous.selectServer,
        previous.selectDatabase,
        previous.activeKey,
      );
      const nextPane = createPane({
        title: `Tab ${activeKey}`,
        key: activeKey,
        closable: true,
        server: nextServer,
        serverName: seedPane?.server === nextServer ? seedPane.serverName : '',
        serverType: seedPane?.server === nextServer ? seedPane.serverType : '',
        database: nextDatabase,
        schemaTables:
          seedPane?.server === nextServer && seedPane?.database === nextDatabase
            ? seedPane.schemaTables || {}
            : {},
      });

      return {
        ...previous,
        activeKey,
        selectServer: nextServer || '0',
        selectDatabase: nextDatabase,
        editorServerType: nextPane.serverType || 'mysql',
        editorSchemaTables: nextPane.schemaTables || {},
        historySql: nextServer ? readHistorySql(nextServer) : [],
        panes: [...previous.panes, nextPane],
      };
    });
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

  const handlePaneSqlChange = (paneKey: string, value: string, snapshot: SelectionSnapshot) => {
    saveCursorValue(snapshot);
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
        ...currentPane,
        sql: value,
      })),
    }));
  };

  const refreshDashboardPane = (pane) => {
    void loadDashboardForPane({
      server: pane.server,
      database: pane.database,
      paneKey: pane.key,
      forceRefresh: true,
    });
  };

  const handleVannaQuestionChange = (paneKey, value) => {
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
        ...currentPane,
        vannaQuestion: value,
      })),
    }));
  };

  const handleVannaCopySql = (sql) => {
    if (!sql) {
      showDialog('当前没有可复制的 SQL');
      return;
    }
    copy(sql);
  };

  const insertGeneratedSql = (paneKey, sql) => {
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
        ...currentPane,
        sql,
        contentTab: 'query',
      })),
    }));
    resetEditorInteraction();
    resetEditorViewport();
  };

  const handleVannaSubmit = async (paneKey) => {
    const current = stateRef.current;
    const pane = getPaneByKey(current.panes, paneKey);

    if (!pane.server || !pane.database) {
      showDialog('请先选择服务器和数据库');
      return;
    }
    if (!pane.vannaQuestion || !pane.vannaQuestion.trim()) {
      showDialog('请输入问题后再生成 SQL');
      return;
    }

    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
        ...currentPane,
        vannaLoading: true,
        vannaError: '',
      })),
    }));

    try {
      const response = await clientRef.current.post('/api/vanna/sql/generate', {
        headers: {
          'Content-Type': 'application/json',
          'User-Token': current.token,
        },
        body: JSON.stringify({
          serverCode: pane.server,
          dbName: pane.database,
          question: pane.vannaQuestion,
        }),
      });

      if (response.status >= 400) {
        throw new Error(response.jsonData?.message || 'AI 问数服务请求失败');
      }

      setState((previous) => ({
        ...previous,
        panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
          ...currentPane,
          vannaLoading: false,
          vannaResult: response.jsonData,
          vannaError: '',
        })),
      }));
    } catch (error) {
      setState((previous) => ({
        ...previous,
        panes: updatePaneByKey(previous.panes, paneKey, (currentPane) => ({
          ...currentPane,
          vannaLoading: false,
          vannaError: error?.message || 'AI 问数服务请求失败',
        })),
      }));
    }
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
            <WorkbenchPane
              deskHeight={state.deskHeight}
              editorRef={editorRef}
              editorSchemaTables={state.editorSchemaTables}
              editorServerType={state.editorServerType}
              historySql={state.historySql}
              pane={pane}
              queryLoading={state.queryLoading}
              onContentTabChange={handleContentTabChange}
              onDashboardRefresh={refreshDashboardPane}
              onDataStyleSwitch={dataStyleSwitch}
              onDeleteHistorySql={deleteHistorySql}
              onExecuteSql={executeSql}
              onExportQueryResult={exportQueryResult}
              onHistorySqlToText={historySqlToText}
              onInsertGeneratedSql={insertGeneratedSql}
              onSelectionChange={saveCursorValue}
              onSqlChange={handlePaneSqlChange}
              onVannaCopySql={handleVannaCopySql}
              onVannaQuestionChange={handleVannaQuestionChange}
              onVannaSubmit={handleVannaSubmit}
            />
          ),
        }))}
      />
    </div>
  );
}

export default PageContent;
