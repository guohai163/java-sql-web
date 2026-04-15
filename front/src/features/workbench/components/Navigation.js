import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Pubsub from 'pubsub-js';
import cookie from 'react-cookies';
import copy from 'copy-to-clipboard';
import { Button, Input, Form, Modal, Select, Spin, Tag, message } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import * as webauthnJson from '@github/webauthn-json';
import { createClient } from '@/shared/api/apiClient';
import logo from '@/shared/assets/brand/logo.svg';
import cache from '@/shared/lib/cache';
import config from '@/shared/config/runtimeConfig';
import dot from '@/features/workbench/assets/dot.gif';
import '@/features/workbench/styles/Navigation.css';

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 24 }} spin />;
const CACHE_TTL = 1000 * 60 * 60 * 24;

let tableResult = false;

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

function getAccessTokenStatusMeta(status) {
  if (status === 'ACTIVE') {
    return { color: 'success', text: '有效' };
  }
  if (status === 'EXPIRED') {
    return { color: 'warning', text: '已过期' };
  }
  return { color: 'default', text: '未申请' };
}

function Navigation() {
  const navigate = useNavigate();
  const [state, setState] = useState({
    serverList: [],
    selectServer: '0',
    dbList: [],
    selectDatabase: '',
    tableList: [],
    spList: [],
    selectTable: '',
    deskHeight: 0,
    showTableColumn: '',
    columntData: [],
    indexData: [],
    token: cookie.load('token') || '',
    tableLoading: false,
    filterTableList: [],
    filterSpList: [],
    passVisible: false,
    accessTokenInfo: null,
    accessTokenLoading: false,
    accessTokenActionLoading: false,
    inputData: {},
    dbGroup: [],
    viewList: [],
  });

  const setStatePatch = (patch) => {
    setState((previous) => ({
      ...previous,
      ...(typeof patch === 'function' ? patch(previous) : patch),
    }));
  };

  const updateInputData = (patch) => {
    setState((previous) => ({
      ...previous,
      inputData:
        typeof patch === 'function'
          ? patch(previous.inputData)
          : {
              ...previous.inputData,
              ...patch,
            },
    }));
  };

  const handleSize = () => {
    setStatePatch({
      deskHeight: window.innerHeight - 158,
    });
  };

  const getServerList = async () => {
    try {
      const client = createClient();
      const [serverListResponse, groupResponse] = await Promise.all([
        client.get('/database/serverlist', {
          headers: { 'User-Token': state.token },
        }),
        client.get('/database/server/group', {
          headers: { 'User-Token': state.token },
        }),
      ]);

      setStatePatch({
        serverList: serverListResponse.jsonData.status
          ? serverListResponse.jsonData.data
          : [],
        dbGroup: groupResponse.jsonData.status ? groupResponse.jsonData.data : [],
      });
    } catch (error) {
      console.log('catch', error);
    }
  };

  useEffect(() => {
    if (!state.token) {
      navigate('/login', { replace: true });
      return undefined;
    }

    void getServerList();
    handleSize();
    window.addEventListener('resize', handleSize);

    return () => {
      window.removeEventListener('resize', handleSize);
    };
  }, []);

  const dbChange = async (dbName) => {
    if (dbName === state.selectDatabase) {
      setStatePatch({
        selectDatabase: '',
      });
      return;
    }

    setStatePatch({
      selectDatabase: dbName,
      tableLoading: true,
      tableList: [],
      spList: [],
      filterSpList: [],
      viewList: [],
    });

    Pubsub.publish('dataSelect', {
      selectServer: state.selectServer,
      selectDatabase: dbName,
      type: 'database',
    });

    const requestKey = `/database/tablelist/${state.selectServer}/${dbName}`;
    const tableData = cache.get(requestKey);

    if (tableData !== null) {
      setStatePatch({
        tableList: tableData,
        filterTableList: tableData,
        tableLoading: false,
      });
      return;
    }

    const client = createClient();
    const response = await client.get(requestKey, {
      headers: { 'User-Token': state.token },
    });

    if (response.jsonData.status) {
      setStatePatch({
        tableList: response.jsonData.data,
        filterTableList: response.jsonData.data,
        tableLoading: false,
      });
      cache.set(requestKey, response.jsonData.data, CACHE_TTL);
      return;
    }

    setStatePatch({
      tableList: [],
      filterTableList: [],
      tableLoading: false,
    });
  };

  const serverChange = async (value) => {
    const client = createClient();
    const response = await client.get(`/database/dblist/${value}`, {
      headers: { 'User-Token': state.token },
    });

    if (response.jsonData.status) {
      setStatePatch({
        selectServer: value,
        dbList: response.jsonData.data,
        selectDatabase: '',
        tableList: [],
        filterTableList: [],
        spList: [],
        filterSpList: [],
        viewList: [],
      });

      Pubsub.publish('dataSelect', {
        selectServer: value,
        type: 'server',
      });
    }
  };

  const filterTable = (event) => {
    const keyword = event.target.value || '';

    setStatePatch((previous) => ({
      filterTableList: previous.tableList.filter((item) =>
        item.tableName.includes(keyword),
      ),
      filterSpList: previous.spList.filter((item) =>
        item.procedureName.includes(keyword),
      ),
    }));
  };

  const getViewsList = async (dbName) => {
    const requestKey = `/database/views/${state.selectServer}/${dbName}`;
    const viewsData = cache.get(requestKey);

    if (viewsData !== null) {
      if (viewsData.length === 0) {
        showDialog('该库无视图');
      }

      setStatePatch({
        viewList: viewsData,
      });
      return;
    }

    const client = createClient();
    const response = await client.get(requestKey, {
      headers: { 'User-Token': state.token },
    });

    if (response.jsonData.status) {
      if (response.jsonData.data.length === 0) {
        showDialog('该库无视图');
      }

      setStatePatch({
        viewList: response.jsonData.data,
      });
      cache.set(requestKey, response.jsonData.data, CACHE_TTL);
      return;
    }

    setStatePatch({
      viewList: [],
    });
  };

  const getSpList = async (dbName) => {
    const requestKey = `/database/storedprocedures/${state.selectServer}/${dbName}`;
    const spData = cache.get(requestKey);

    if (spData !== null) {
      if (spData.length === 0) {
        showDialog('该库无存储过程');
      }

      setStatePatch({
        spList: spData,
        filterSpList: spData,
      });
      return;
    }

    const client = createClient();
    const response = await client.get(requestKey, {
      headers: { 'User-Token': state.token },
    });

    if (response.jsonData.status) {
      if (response.jsonData.data.length === 0) {
        showDialog('该库无存储过程');
      }

      setStatePatch({
        spList: response.jsonData.data,
        filterSpList: response.jsonData.data,
      });
      cache.set(requestKey, response.jsonData.data, CACHE_TTL);
      return;
    }

    setStatePatch({
      spList: [],
      filterSpList: [],
    });
  };

  const viewChange = (viewName) => {
    Pubsub.publish('dataSelect', {
      selectServer: state.selectServer,
      selectDatabase: state.selectDatabase,
      viewName,
      type: 'view',
    });
  };

  const spChange = (spName) => {
    Pubsub.publish('dataSelect', {
      selectServer: state.selectServer,
      selectDatabase: state.selectDatabase,
      spName,
      type: 'sp',
    });
  };

  const sendColumnName = (columnName) => {
    Pubsub.publish('dataSelect', {
      selectServer: state.selectServer,
      selectDatabase: state.selectDatabase,
      selectColumn: columnName,
      type: 'column',
    });
  };

  const sendTableName = (tableName) => {
    const selectServer = state.selectServer;
    const selectDatabase = state.selectDatabase;
    tableResult = false;

    window.setTimeout(() => {
      if (tableResult !== false) {
        return;
      }

      Pubsub.publish('dataSelect', {
        selectServer,
        selectDatabase,
        selectTable: tableName,
        type: 'tableName',
      });
    }, 300);
  };

  const tableChange = (tableName) => {
    tableResult = true;
    setStatePatch({
      selectTable: tableName,
    });
    Pubsub.publish('dataSelect', {
      selectServer: state.selectServer,
      selectDatabase: state.selectDatabase,
      selectTable: tableName,
      type: 'table',
    });
  };

  const showTableColumn = async (tableName) => {
    if (state.showTableColumn === tableName) {
      setStatePatch({
        showTableColumn: '',
      });
      return;
    }

    setStatePatch({
      showTableColumn: tableName,
    });

    const client = createClient();
    const [columnResponse, indexResponse] = await Promise.all([
      client.get(
        `/database/columnslist/${state.selectServer}/${state.selectDatabase}/${tableName}`,
        { headers: { 'User-Token': state.token } },
      ),
      client.get(
        `/database/indexeslist/${state.selectServer}/${state.selectDatabase}/${tableName}`,
        { headers: { 'User-Token': state.token } },
      ),
    ]);

    setStatePatch({
      columntData: columnResponse.jsonData.status ? columnResponse.jsonData.data : [],
      indexData: indexResponse.jsonData.status ? indexResponse.jsonData.data : [],
    });
  };

  const logout = async () => {
    const client = createClient();
    const response = await client.get('/user/logout', {
      headers: { 'User-Token': state.token },
    });

    if (response.jsonData.status) {
      cookie.remove('token', { path: '/' });
      setStatePatch({
        token: '',
      });
      confirm({
        title: '提示',
        content: response.jsonData.message,
        onOk() {
          navigate('/login');
        },
        onCancel() {
          navigate('/login');
        },
      });
    }
  };

  const jumpAdmin = () => {
    if (config.userName === 'admin') {
      navigate('/admin');
      return;
    }

    showDialog('您无权限进入管理页面');
  };

  const modalHandleOk = async () => {
    if (!(state.inputData.userNewPassword || '').trim()) {
      showDialog('请输入新密码');
      return;
    }
    const client = createClient();
    const response = await client.post('/api/backstage/change_new_pass', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: state.inputData.userNewPassword || '',
    });

    if (response.jsonData.status === true) {
      showDialog('密码修改成功');
      setStatePatch({
        passVisible: false,
      });
      return;
    }

    showDialog(response.jsonData.data);
  };

  const passKeyBind = async () => {
    if (!webauthnJson.supported()) {
      showDialog('当前系统环境无法开启passKey功能');
      return;
    }

    const client = createClient();
    const response = await client.get('/webauthn/create', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status !== true) {
      showDialog(response.jsonData.data);
      return;
    }

    try {
      const publicKeyCredential = await webauthnJson.create(
        JSON.parse(response.jsonData.data),
      );
      const registerResponse = await client.post('/webauthn/register', {
        headers: {
          'Content-Type': 'application/json',
          'User-Token': state.token,
        },
        body: JSON.stringify(publicKeyCredential),
      });

      if (registerResponse.jsonData.status === true) {
        showDialog('passKey绑定成功');
        setStatePatch({
          passVisible: false,
        });
        return;
      }

      showDialog(registerResponse.jsonData.data);
    } catch (error) {
      showDialog('passKey绑定失败');
    }
  };

  const loadAccessTokenInfo = async () => {
    setStatePatch({
      accessTokenLoading: true,
    });

    const client = createClient();
    const response = await client.get('/user/access-token', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status === true) {
      setStatePatch({
        accessTokenInfo: response.jsonData.data,
        accessTokenLoading: false,
      });
      return;
    }

    setStatePatch({
      accessTokenInfo: null,
      accessTokenLoading: false,
    });
    showDialog(response.jsonData.message || '访问令牌信息加载失败');
  };

  const accessTokenAction = async (method, url) => {
    setStatePatch({
      accessTokenActionLoading: true,
    });

    const client = createClient();
    const response = await client[method](url, {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status === true) {
      setStatePatch({
        accessTokenInfo: response.jsonData.data,
        accessTokenActionLoading: false,
      });
      message.success(response.jsonData.message || '访问令牌操作成功');
      return;
    }

    setStatePatch({
      accessTokenActionLoading: false,
    });
    showDialog(response.jsonData.message || '访问令牌操作失败');
  };

  const createAccessToken = async () => {
    await accessTokenAction('post', '/user/access-token');
  };

  const renewAccessToken = async () => {
    await accessTokenAction('put', '/user/access-token/renew');
  };

  const resetAccessToken = async () => {
    await accessTokenAction('put', '/user/access-token/reset');
  };

  const copyAccessToken = () => {
    const accessToken = state.accessTokenInfo?.accessToken;
    if (!accessToken) {
      showDialog('当前没有可复制的完整访问令牌');
      return;
    }
    copy(accessToken);
    message.success('访问令牌已复制');
  };

  const modalHandleCancel = () => {
    setStatePatch({
      passVisible: false,
      accessTokenInfo: null,
      accessTokenLoading: false,
      accessTokenActionLoading: false,
      inputData: {},
    });
  };

  const showPassModal = async () => {
    setStatePatch({
      passVisible: true,
    });
    await loadAccessTokenInfo();
  };

  const onInputChange = (event) => {
    updateInputData({
      [event.target.id]: event.target.value,
    });
  };

  const handleLink = (event, action) => {
    event.preventDefault();
    void action();
  };

  return (
    <div id="navigation">
      <div id="navigation_resizer"></div>
      <div id="navigation_content">
        <div id="navigation_header">
          <div id="logo">
            <img src={logo} alt="logo" />
            {config.version}
          </div>
          <div id="navipanellinks">
            <a href="/guid" rel="noreferrer" target="_blank" title="常用SQL">
              <img src={dot} alt="常用SQL" className="icon ic_s_sqlguid" />
            </a>
            <a href="/" title="刷新" onClick={(event) => handleLink(event, getServerList)}>
              <img src={dot} alt="刷新" className="icon ic_s_reload" />
            </a>
            <a href="/" title="修改密码" onClick={(event) => handleLink(event, showPassModal)}>
              <img src={dot} alt="修改密码" className="icon ic_u_pass" />
            </a>
            <a href="/" title="passkey" onClick={(event) => handleLink(event, passKeyBind)}>
              <img src={dot} alt="passkey" className="icon ic_w_authn" />
            </a>
            <a href="/" title="设置" onClick={(event) => handleLink(event, jumpAdmin)}>
              <img
                src={dot}
                alt="setting"
                className={config.userName === 'admin' ? 'icon ic_s_cog' : 'hide'}
              />
            </a>
            <a href="/" title="退出" onClick={(event) => handleLink(event, logout)}>
              <img src={dot} alt="exit" className="icon ic_s_loggoff" />
            </a>
          </div>
        </div>
        <div id="navigation_tree">
          <div className="navigation_server">
            <label>服务器：</label>
            <Select
              placeholder="请选择服务器"
              style={{ width: 200 }}
              value={state.selectServer === '0' ? undefined : state.selectServer}
              onChange={serverChange}
            >
              {state.dbGroup.map((group) => (
                <Select.OptGroup key={group} label={group}>
                  {state.serverList
                    .filter((item) => item.dbGroup === group)
                    .map((server) => (
                      <Select.Option key={server.code} value={server.code}>
                        {server.dbServerName}
                      </Select.Option>
                    ))}
                </Select.OptGroup>
              ))}
            </Select>
          </div>
          <div id="navigation_tree_content" style={{ height: state.deskHeight }}>
            <ul>
              {state.dbList.map((db) => (
                <li className="database" key={db.dbName}>
                  <div className="block">
                    <i></i>
                    <b></b>
                    <a
                      className="expander loaded"
                      href="/"
                      onClick={(event) => handleLink(event, () => dbChange(db.dbName))}
                    >
                      <span className="hide aPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                      <span className="hide vPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                      <span className="hide pos">0</span>
                      <img
                        src={dot}
                        title="扩展/收起"
                        alt="扩展/收起"
                        className={
                          state.selectDatabase === db.dbName
                            ? 'icon ic_b_minus'
                            : 'icon ic_b_plus'
                        }
                      />
                    </a>
                  </div>
                  <div className="block">
                    <a
                      href="/"
                      onClick={(event) => handleLink(event, () => dbChange(db.dbName))}
                    >
                      <img src={dot} alt="数据库操作" className="icon ic_s_db" />
                    </a>
                  </div>
                  <a
                    className="hover_show_full"
                    href="/"
                    onClick={(event) => handleLink(event, () => dbChange(db.dbName))}
                  >
                    {db.dbName}
                  </a>
                  <div
                    className={
                      state.tableLoading && state.selectDatabase === db.dbName
                        ? 'clearfloat'
                        : 'hide'
                    }
                  >
                    <Spin indicator={antIcon} />
                  </div>
                  <div
                    className={state.selectDatabase === db.dbName ? 'list_container' : 'hide'}
                  >
                    <ul>
                      <li className="filter_input">
                        <Input
                          allowClear
                          placeholder="Filter"
                          size="small"
                          onChange={filterTable}
                        />
                      </li>
                      {state.selectDatabase !== db.dbName
                        ? null
                        : state.filterTableList.map((table) => (
                            <li className="view" key={table.tableName}>
                              <div className="block">
                                <i></i>
                                <a className="expander" href="/">
                                  <span className="hide pos2_name">views</span>
                                  <span className="hide pos2_value">0</span>
                                  <img
                                    src={dot}
                                    title="扩展/收起"
                                    alt="扩展/收起"
                                    className={
                                      state.showTableColumn === table.tableName
                                        ? 'icon ic_b_minus'
                                        : 'icon ic_b_plus'
                                    }
                                    onClick={(event) => {
                                      event.preventDefault();
                                      void showTableColumn(table.tableName);
                                    }}
                                  />
                                </a>
                              </div>
                              <div className="block">
                                <a href="/">
                                  <img
                                    src={dot}
                                    title="视图"
                                    alt="视图"
                                    className="icon ic_b_props"
                                  />
                                </a>
                              </div>
                              <a
                                className="hover_show_full"
                                href="/"
                                title=""
                                onClick={(event) => handleLink(event, () => sendTableName(table.tableName))}
                                onDoubleClick={(event) => {
                                  event.preventDefault();
                                  tableChange(table.tableName);
                                }}
                              >
                                {' '}
                                {table.tableName} ({table.tableRows})
                              </a>
                              <div className="clearfloat"></div>
                              <div
                                className={
                                  state.showTableColumn === table.tableName
                                    ? 'list_container'
                                    : 'hide'
                                }
                              >
                                <ul>
                                  {state.showTableColumn !== table.tableName
                                    ? null
                                    : state.columntData.map((column) => (
                                        <li
                                          key={column.columnName}
                                          onClick={() => sendColumnName(column.columnName)}
                                        >
                                          {column.columnName}({column.columnType}
                                          {column.columnLength === ''
                                            ? ''
                                            : `(${column.columnLength})`}
                                          ,{column.columnIsNull})
                                          <br /> -{' '}
                                          <Tag color="green">
                                            {column.columnComment === ''
                                              ? 'NULL'
                                              : column.columnComment}
                                          </Tag>
                                        </li>
                                      ))}
                                  {state.showTableColumn !== table.tableName
                                    ? null
                                    : state.indexData.map((indexData) => (
                                        <li key={indexData.indexName}>
                                          <img
                                            src={dot}
                                            title="视图"
                                            alt="视图"
                                            className="icon ic_b_views"
                                          />
                                          {indexData.indexName}[{indexData.indexKeys}]
                                        </li>
                                      ))}
                                </ul>
                              </div>
                            </li>
                          ))}
                      <li className="view" key="procedure">
                        <a
                          href="/"
                          onClick={(event) => handleLink(event, () => getSpList(db.dbName))}
                        >
                          <img
                            src={dot}
                            alt="toggle routines"
                            className={
                              state.filterSpList.length === 0 ? 'icon ic_b_plus' : 'icon ic_b_minus'
                            }
                          />
                          <img
                            src={dot}
                            title="存储过程"
                            alt="存储过程"
                            className="icon ic_b_routines"
                          />
                          存储过程
                        </a>
                        <div
                          className={
                            state.filterSpList.length === 0 ? 'hide' : 'list_container'
                          }
                        >
                          <ul>
                            {state.filterSpList.map((sp) => (
                              <li className="view" key={sp.procedureName}>
                                <div className="block">
                                  <a href="/">
                                    <img
                                      src={dot}
                                      title="视图"
                                      alt="视图"
                                      className="icon ic_b_routines"
                                    />
                                  </a>
                                </div>
                                <a
                                  className="hover_show_full"
                                  href="/"
                                  title=""
                                  onClick={(event) => handleLink(event, () => spChange(sp.procedureName))}
                                >
                                  {' '}
                                  {sp.procedureName}
                                </a>
                                <div className="clearfloat"></div>
                              </li>
                            ))}
                          </ul>
                        </div>
                      </li>
                      <li className="view" key="views">
                        <a
                          href="/"
                          onClick={(event) => handleLink(event, () => getViewsList(db.dbName))}
                        >
                          <img
                            src={dot}
                            alt="toggle views"
                            className={
                              state.viewList.length === 0 ? 'icon ic_b_plus' : 'icon ic_b_minus'
                            }
                          />
                          <img
                            src={dot}
                            title="视图"
                            alt="视图"
                            className="icon ic_b_views"
                          />
                          视图
                        </a>
                        <div
                          className={state.viewList.length === 0 ? 'hide' : 'list_container'}
                        >
                          <ul>
                            {state.viewList.map((view) => (
                              <li className="view" key={view.viewName}>
                                <div className="block">
                                  <a href="/">
                                    <img
                                      src={dot}
                                      title="视图"
                                      alt="视图"
                                      className="icon ic_b_views"
                                    />
                                  </a>
                                </div>
                                <a
                                  className="hover_show_full"
                                  href="/"
                                  title=""
                                  onClick={(event) => handleLink(event, () => viewChange(view.viewName))}
                                >
                                  {' '}
                                  {view.viewName}
                                </a>
                                <div className="clearfloat"></div>
                              </li>
                            ))}
                          </ul>
                        </div>
                      </li>
                    </ul>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
      <Modal
        open={state.passVisible}
        title="账号安全"
        onCancel={modalHandleCancel}
        onOk={modalHandleOk}
        okText="更新密码"
        cancelText="关闭"
      >
        <Form labelCol={{ span: 7 }} size="small">
          <Form.Item label="请输入新密码" rules={[{ required: true, message: '请输入密码!' }]}>
            <Input.Password
              id="userNewPassword"
              value={state.inputData.userNewPassword || ''}
              onChange={onInputChange}
            />
          </Form.Item>
        </Form>
        <div className="security-section">
          <div className="security-section-title">访问令牌</div>
          {state.accessTokenLoading ? (
            <div className="security-loading">
              <Spin indicator={antIcon} />
            </div>
          ) : (
            <>
              <div className="security-meta">
                <Tag color={getAccessTokenStatusMeta(state.accessTokenInfo?.accessTokenStatus).color}>
                  {getAccessTokenStatusMeta(state.accessTokenInfo?.accessTokenStatus).text}
                </Tag>
                <span>
                  到期时间：{state.accessTokenInfo?.accessTokenExpireTime || '未申请'}
                </span>
              </div>
              {state.accessTokenInfo?.hasAccessToken ? (
                <div className="security-token-box">
                  {state.accessTokenInfo.accessTokenFullVisible
                    ? state.accessTokenInfo.accessToken
                    : state.accessTokenInfo.maskedAccessToken}
                </div>
              ) : (
                <div className="security-help">当前还没有访问令牌</div>
              )}
              {state.accessTokenInfo?.accessTokenFullVisible ? (
                <Button size="small" type="primary" onClick={copyAccessToken}>
                  复制完整令牌
                </Button>
              ) : null}
              {state.accessTokenInfo?.authStatus !== 'BIND' ? (
                <div className="security-help">需先绑定OTP才能申请或续期访问令牌</div>
              ) : null}
              <div className="security-actions">
                {state.accessTokenInfo?.canCreateAccessToken ? (
                  <Button
                    loading={state.accessTokenActionLoading}
                    type="primary"
                    onClick={() => {
                      void createAccessToken();
                    }}
                  >
                    申请访问令牌
                  </Button>
                ) : null}
                {state.accessTokenInfo?.canRenewAccessToken ? (
                  <Button
                    loading={state.accessTokenActionLoading}
                    onClick={() => {
                      void renewAccessToken();
                    }}
                  >
                    续期90天
                  </Button>
                ) : null}
                {state.accessTokenInfo?.canResetAccessToken ? (
                  <Button
                    danger
                    loading={state.accessTokenActionLoading}
                    onClick={() => {
                      void resetAccessToken();
                    }}
                  >
                    重置令牌
                  </Button>
                ) : null}
              </div>
              <div className="security-help">
                接口请求时请在 Header 中传 <code>Authorization: Bearer &lt;token&gt;</code>
              </div>
              {state.accessTokenInfo?.hasAccessToken && !state.accessTokenInfo?.accessTokenFullVisible ? (
                <div className="security-help">
                  完整令牌只会在申请成功或重置成功时显示一次，请及时复制保存。
                </div>
              ) : null}
            </>
          )}
        </div>
      </Modal>
    </div>
  );
}

export default Navigation;
