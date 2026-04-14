import React, { useEffect, useRef, useState } from 'react';
import Pubsub from 'pubsub-js';
import cookie from 'react-cookies';
import { CSVLink } from 'react-csv';
import { Controlled as CodeMirror } from 'react-codemirror2';
import { Empty, List, Modal, Spin, Switch, Tabs } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import dot from './images/dot.gif';
import Spreadsheet from './Spreadsheet';
import DataDisplayFast from './DataDisplayFast';
import './PageContent.css';
import 'codemirror/lib/codemirror.css';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/hint/sql-hint.js';
import 'codemirror/mode/sql/sql';
import 'codemirror/theme/idea.css';
import { createClient } from './apiClient';

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
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
    dataDisplayStyle: true,
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

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

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
            dataDisplayStyle: response.jsonData.data.length <= 2000,
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
    setState((previous) => ({
      ...previous,
      panes: updatePaneByKey(previous.panes, previous.activeKey, (pane) => ({
        ...pane,
        dataDisplayStyle: checked,
      })),
    }));
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
                    <CSVLink data={pane.queryResult}>导出查询结果</CSVLink>
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
