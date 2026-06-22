import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Pubsub from 'pubsub-js';
import cookie from 'react-cookies';
import copy from 'copy-to-clipboard';
import { Modal, message } from 'antd';
import * as webauthnJson from '@github/webauthn-json';
import { createClient } from '@/shared/api/apiClient';
import cache from '@/shared/lib/cache';
import config from '@/shared/config/runtimeConfig';
import NavigationView from '@/features/workbench/components/NavigationView';
import {
  buildPasskeyDomainMessage,
  formatServerLabel,
  getAccessTokenStatusMeta,
  isRpIdCompatible,
  matchServerKeyword,
  normalizeArray,
  sortTablesByAsciiName,
} from '@/features/workbench/lib/navigationModel';
import '@/features/workbench/styles/Navigation.css';

const { confirm } = Modal;
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

interface NavigationState {
  serverList: any[];
  selectServer: string;
  dbList: any[];
  selectDatabase: string;
  tableList: any[];
  spList: any[];
  selectTable: string;
  deskHeight: number;
  showTableColumn: string;
  columntData: any[];
  indexData: any[];
  token: string;
  tableLoading: boolean;
  filterTableList: any[];
  filterSpList: any[];
  passVisible: boolean;
  accessTokenInfo: any;
  accessTokenLoading: boolean;
  accessTokenActionLoading: boolean;
  inputData: Record<string, any>;
  dbGroup: any[];
  viewList: any[];
}

function Navigation() {
  const navigate = useNavigate();
  const [state, setState] = useState<NavigationState>({
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

  useEffect(() => {
    const resizer = document.getElementById('navigation_resizer');
    const nav = document.getElementById('navigation');
    let isResizing = false;

    const startResizing = () => {
      isResizing = true;
      resizer?.classList.add('resizing');
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    };

    const stopResizing = () => {
      isResizing = false;
      resizer?.classList.remove('resizing');
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    const resize = (event) => {
      if (!isResizing || !nav) return;
      const newWidth = event.clientX;
      if (newWidth > 200 && newWidth < 800) {
        document.documentElement.style.setProperty('--workbench-nav-width', `${newWidth}px`);
      }
    };

    resizer?.addEventListener('mousedown', startResizing);
    window.addEventListener('mousemove', resize);
    window.addEventListener('mouseup', stopResizing);

    return () => {
      resizer?.removeEventListener('mousedown', startResizing);
      window.removeEventListener('mousemove', resize);
      window.removeEventListener('mouseup', stopResizing);
    };
  }, []);

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
      const sortedTables = sortTablesByAsciiName(tableData);
      setStatePatch({
        tableList: sortedTables,
        filterTableList: sortedTables,
        tableLoading: false,
      });
      return;
    }

    const client = createClient();
    const response = await client.get(requestKey, {
      headers: { 'User-Token': state.token },
    });

    if (response.jsonData.status) {
      const sortedTables = sortTablesByAsciiName(response.jsonData.data);
      setStatePatch({
        tableList: sortedTables,
        filterTableList: sortedTables,
        tableLoading: false,
      });
      cache.set(requestKey, sortedTables, CACHE_TTL);
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
      const nextViews = normalizeArray(response.jsonData.data);
      if (nextViews.length === 0) {
        showDialog('该库无视图');
      }

      setStatePatch({
        viewList: nextViews,
      });
      cache.set(requestKey, nextViews, CACHE_TTL);
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
      const nextProcedures = normalizeArray(response.jsonData.data);
      if (nextProcedures.length === 0) {
        showDialog('该库无存储过程');
      }

      setStatePatch({
        spList: nextProcedures,
        filterSpList: nextProcedures,
      });
      cache.set(requestKey, nextProcedures, CACHE_TTL);
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
      columntData: columnResponse.jsonData.status ? normalizeArray(columnResponse.jsonData.data) : [],
      indexData: indexResponse.jsonData.status ? normalizeArray(indexResponse.jsonData.data) : [],
    });
  };

  const logout = async () => {
    try {
      const client = createClient();
      const response = await client.get('/user/logout', {
        headers: { 'User-Token': state.token },
      });

      if (!response.jsonData.status) {
        showDialog(response.jsonData.message || response.jsonData.data || '注销失败');
        return;
      }

      message.success(response.jsonData.message || '注销成功');
    } finally {
      cookie.remove('token', { path: '/' });
      setStatePatch({
        token: '',
      });
      navigate('/login', { replace: true });
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
    const response = await client.post('/user/password', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify({ passWord: state.inputData.userNewPassword || '' }),
    });

    if (response.jsonData.status === true) {
      showDialog('密码修改成功');
      setStatePatch({
        passVisible: false,
      });
      return;
    }

    showDialog(response.jsonData.message || response.jsonData.data);
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
      const requestJson = JSON.parse(response.jsonData.data);
      const rpId = requestJson?.publicKey?.rp?.id;
      if (!isRpIdCompatible(rpId)) {
        showDialog(buildPasskeyDomainMessage(rpId), 'passkey 域名不匹配');
        return;
      }

      const publicKeyCredential = await webauthnJson.create(requestJson);
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
      showDialog(error?.message || 'passKey绑定失败');
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

  return (
    <NavigationView
      copyAccessToken={copyAccessToken}
      createAccessToken={createAccessToken}
      dbChange={dbChange}
      filterTable={filterTable}
      formatServerLabel={formatServerLabel}
      getAccessTokenStatusMeta={getAccessTokenStatusMeta}
      getServerList={getServerList}
      getSpList={getSpList}
      getViewsList={getViewsList}
      jumpAdmin={jumpAdmin}
      logout={logout}
      matchServerKeyword={matchServerKeyword}
      modalHandleCancel={modalHandleCancel}
      modalHandleOk={modalHandleOk}
      onInputChange={onInputChange}
      passKeyBind={passKeyBind}
      renewAccessToken={renewAccessToken}
      resetAccessToken={resetAccessToken}
      sendColumnName={sendColumnName}
      sendTableName={sendTableName}
      serverChange={serverChange}
      showPassModal={showPassModal}
      showTableColumn={showTableColumn}
      spChange={spChange}
      state={state}
      tableChange={tableChange}
      viewChange={viewChange}
    />
  );
}

export default Navigation;
