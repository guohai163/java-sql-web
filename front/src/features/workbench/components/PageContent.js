import React, { useEffect, useRef, useState } from 'react';
import Pubsub from 'pubsub-js';
import cookie from 'react-cookies';
import { Controlled as CodeMirror } from 'react-codemirror2';
import { Empty, List, Modal, Spin, Switch, Tabs } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import { createClient } from '@/shared/api/apiClient';
import dot from '@/features/workbench/assets/dot.gif';
import DataDisplayFast from '@/features/workbench/components/DataDisplayFast';
import Spreadsheet from '@/features/workbench/components/Spreadsheet';
import '@/features/workbench/styles/PageContent.css';
import 'codemirror/lib/codemirror.css';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/hint/sql-hint.js';
import 'codemirror/mode/sql/sql';
import 'codemirror/theme/idea.css';

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;
const RENDER_MODE_STORAGE_KEY = 'jsw_query_render_mode';
const LARGE_RESULT_SIZE = 2000;

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
    dataDisplayStyle: readRenderModePreference(),
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
    beforeSql: '',
    rearSql: '',
    options: {
      lineNumbers: true,
      mode: { name: 'text/x-mysql' },
      extraKeys: { Ctrl: 'autocomplete' },
      theme: 'idea',
    },
    activeKey: initialPanes[0].key,
    panes: initialPanes,
    deskHeight: document.body.clientHeight - 460,
  });
  const clientRef = useRef(createClient());
  const newTabIndexRef = useRef(2);
  const stateRef = useRef(state);
  const editorRef = useRef(null);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  const resetEditorViewport = () => {
    window.requestAnimationFrame(() => {
      if (!editorRef.current) {
        return;
      }
      editorRef.current.scrollTo(null, 0);
      editorRef.current.setCursor({ line: 0, ch: 0 });
    });
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

  const handleDataSelect = async (data) => {
    const current = stateRef.current;

    if (data.type === 'table') {
      const response = await clientRef.current.get(
        `/database/serverinfo/${data.selectServer}`,
        { headers: { 'User-Token': current.token } },
      );
      let sql = '';

      if (
        response.jsonData.data.dbServerType === 'mssql' ||
        response.jsonData.data.dbServerType === 'mssql_druid'
      ) {
        sql = `SELECT top 100 * FROM [${data.selectTable}]`;
      } else if (response.jsonData.data.dbServerType === 'mysql') {
        sql = `SELECT * FROM \`${data.selectDatabase}\`.\`${data.selectTable}\` limit 100`;
      }

      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: data.selectDatabase,
        selectTable: data.selectTable,
        historySql: readHistorySql(data.selectServer),
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          sql,
          server: data.selectServer,
          serverName: response.jsonData.data.dbServerName,
          serverType: response.jsonData.data.dbServerType,
          database: data.selectDatabase,
        })),
      }));
      resetEditorViewport();
      return;
    }

    if (data.type === 'tableName') {
      setState((previous) => {
        const sql = `${previous.beforeSql} ${data.selectTable} ${previous.rearSql}`;
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
        const sql = `${previous.beforeSql} ${data.selectColumn} ${previous.rearSql}`;
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

      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: data.selectDatabase,
        historySql: readHistorySql(data.selectServer),
        options: {
          lineNumbers: true,
          mode: { name: 'text/x-mysql' },
          extraKeys: { Ctrl: 'autocomplete' },
          hintOptions: {
            tables: tableColumnResponse.jsonData.data,
          },
          theme: 'idea',
        },
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          server: data.selectServer,
          database: data.selectDatabase,
          serverName: serverResponse.jsonData.data.dbServerName,
          serverType: serverResponse.jsonData.data.dbServerType,
        })),
      }));
      return;
    }

    if (data.type === 'server') {
      const response = await clientRef.current.get(
        `/database/serverinfo/${data.selectServer}`,
        { headers: { 'User-Token': current.token } },
      );

      setState((previous) => ({
        ...previous,
        selectServer: data.selectServer,
        selectDatabase: '',
        historySql: readHistorySql(data.selectServer),
        panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
          ...pane,
          server: data.selectServer,
          serverName: response.jsonData.data.dbServerName,
          serverType: response.jsonData.data.dbServerType,
        })),
      }));
    }
  };

  const saveCursorValue = (editor) => {
    const totalLine = editor.lineCount();
    const currentCursor = editor.getCursor();
    let beforeCount = 0;
    let rearCount = 0;

    for (let index = 0; index < totalLine; index += 1) {
      const lineText = editor.getLine(index);
      if (currentCursor.line > index) {
        beforeCount += lineText.length;
      } else if (currentCursor.line < index) {
        rearCount += lineText.length;
      } else {
        beforeCount += currentCursor.ch;
        rearCount += lineText.length - currentCursor.ch;
      }
    }

    const sqlValue = editor.getValue();
    setState((previous) => ({
      ...previous,
      beforeSql: sqlValue.substring(0, beforeCount + 1),
      rearSql: sqlValue.substring(beforeCount + 1),
    }));
  };

  const mouseSelected = (editor) => {
    saveCursorValue(editor);

    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
        ...pane,
        selectedSql: editor.getSelection(),
      })),
    }));
  };

  const executeSql = async () => {
    const current = stateRef.current;
    const currentPane = getPaneByKey(current.panes, current.activeKey);
    const sql = currentPane.selectedSql === '' ? currentPane.sql : currentPane.selectedSql;

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
        ...pane,
        queryResult: [],
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
            ...pane,
            dataDisplayStyle: shouldUseModernDisplay,
            queryResult: response.jsonData.data,
            dataAreaRefresh: [sql],
          })),
        };
      });
      return;
    }

    setState((previous) => ({
      ...previous,
      queryLoading: false,
      panes: updatePaneByKey(previous.panes, currentPane.key, (pane) => ({
        ...pane,
        queryResult: [],
        dataAreaRefresh: [],
      })),
    }));
    showDialog(response.jsonData.message, '错误');
  };

  const historySqlToText = (sqlScript) => {
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
        ...pane,
        sql: sqlScript,
      })),
    }));
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
        historySql: pane.server ? readHistorySql(pane.server) : [],
      };
    });
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
  };

  const handleTabsEdit = (targetKey, action) => {
    if (action === 'add') {
      addTab();
      return;
    }

    removeTab(targetKey);
  };

  return (
    <div className="right_area">
      <Tabs
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
                <div id="serverinfo">
                  <img src={dot} alt="SERVERIMG" className="icon ic_s_host " />
                  服务器: {pane.serverName} ({pane.serverType})
                  <span className={pane.database === '' ? 'hide' : 'none'}>
                    &gt;&gt;{' '}
                    <img src={dot} className="icon ic_s_db " alt="DBIMG" />
                    数据库: {pane.database}
                  </span>
                </div>
              </div>
              <div className="page_content">
                <div id="queryboxContainer">
                  <fieldset id="queryboxf">
                    <div id="queryfieldscontainer">
                      <div id="sqlquerycontainer">
                        <CodeMirror
                          editorDidMount={(editor) => {
                            editorRef.current = editor;
                            editor.scrollTo(null, 0);
                          }}
                          onBeforeChange={(editor, metadata, value) => {
                            setState((previous) => ({
                              ...previous,
                              panes: updatePaneByKey(previous.panes, pane.key, (currentPane) => ({
                                ...currentPane,
                                sql: value,
                              })),
                            }));
                          }}
                          onCursorActivity={mouseSelected}
                          options={state.options}
                          value={pane.sql}
                        />
                        * 敲入关键字首字母后可以使用Ctrl进行快速补全，选中部分SQL只会执行选中部分的语句！
                      </div>
                      <label>历史记录</label>
                      <div id="tablefieldscontainer">
                        <List
                          dataSource={state.historySql}
                          renderItem={(item) => (
                            <List.Item key={item}>
                              <a onClick={() => historySqlToText(item)}>
                                {item.length > 60 ? `${item.substring(0, 60)}...` : item}
                              </a>
                              <button
                                className="btn_right"
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
                <fieldset id="queryboxfooter" className="tblFooters">
                  <Switch
                    checked={pane.dataDisplayStyle}
                    checkedChildren="新版"
                    unCheckedChildren="旧版"
                    onChange={dataStyleSwitch}
                  />
                  <input
                    className="btn btn-primary"
                    id="button_submit_query"
                    name="SQL"
                    tabIndex="200"
                    type="submit"
                    value="执行SQL"
                    onClick={executeSql}
                  />
                  {pane.queryResult.length !== 0 ? (
                    <button type="button" onClick={() => exportQueryResult(pane)}>
                      导出查询结果
                    </button>
                  ) : (
                    <span></span>
                  )}
                  <div className="clearfloat"></div>
                </fieldset>
                <div
                  className={
                    state.queryLoading || pane.queryResult.length === 0
                      ? 'hide'
                      : 'responsivetable'
                  }
                >
                  <div className="container-wrap" style={{ height: state.deskHeight }}>
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
                <div className={state.queryLoading ? 'query_load' : 'hide'}>
                  <Spin indicator={antIcon} />
                  数据查询中...
                </div>
                <div
                  className={
                    !state.queryLoading && pane.queryResult.length === 0
                      ? 'query_load'
                      : 'hide'
                  }
                >
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
