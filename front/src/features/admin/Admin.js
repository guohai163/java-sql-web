import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import cookie from 'react-cookies';
import copy from 'copy-to-clipboard';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Layout,
  Menu,
  Modal,
  Select,
  Space,
  Tag,
  Table,
  message,
} from 'antd';
import {
  ConsoleSqlOutlined,
  DatabaseOutlined,
  EditOutlined,
  LinkOutlined,
  TableOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import AdminDashboard from '@/features/admin/components/AdminDashboard';
import { getServerTypeLabel } from '@/features/workbench/lib/serverType';
import { createClient } from '@/shared/api/apiClient';
import { formatVersionLabel } from '@/shared/lib/version';
import './Admin.css';

const { confirm } = Modal;
const { Content, Footer, Header, Sider } = Layout;

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

function getAccountStatusMeta(status) {
  if (status === 'ACTIVE') {
    return { color: 'success', text: '正常' };
  }
  if (status === 'PENDING_ACTIVATION') {
    return { color: 'processing', text: '待激活' };
  }
  if (status === 'PENDING_PASSWORD_RESET') {
    return { color: 'warning', text: '待重置密码' };
  }
  if (status === 'PENDING_OTP_RESET') {
    return { color: 'warning', text: '待重绑OTP' };
  }
  return { color: 'default', text: status || '未知' };
}

function getPendingTaskMeta(taskType) {
  if (taskType === 'ACTIVATE') {
    return { color: 'processing', text: '激活' };
  }
  if (taskType === 'RESET_PASSWORD') {
    return { color: 'warning', text: '重置密码' };
  }
  if (taskType === 'RESET_OTP') {
    return { color: 'warning', text: '重绑OTP' };
  }
  return { color: 'default', text: '无' };
}

function getAuthStatusMeta(status) {
  if (status === 'BIND') {
    return { color: 'success', text: '已绑定' };
  }
  if (status === 'BINDING') {
    return { color: 'processing', text: '绑定中' };
  }
  return { color: 'default', text: status || '未绑定' };
}

function createEmptyQueryLogCursor(pageSize = 25) {
  return {
    items: [],
    pageSize,
    firstCode: null,
    lastCode: null,
    hasOlder: false,
    hasNewer: false,
  };
}

function Admin() {
  const navigate = useNavigate();
  const [state, setState] = useState({
    menuSelect: '1',
    version: '',
    dashboardData: null,
    dashboardLoading: false,
    dashboardUpdatedAt: '',
    dashboardFilter: {
      range: '24h',
      grain: 'hour',
    },
    queryLogCursor: createEmptyQueryLogCursor(),
    connList: [],
    testingServerCode: null,
    configVisible: false,
    userAddVisible: false,
    confirmLoading: false,
    userGroupAddVisible: false,
    permissionAddVisible: false,
    druidList: [],
    userList: [],
    userSearchKeyword: '',
    linkVisible: false,
    issuedLinkTitle: '',
    issuedLinkData: null,
    userGroupList: [],
    dbPermissionList: [],
    inputData: {},
    permissionEditGroupCode: 1,
    permissionEditServerList: [],
    userGroupEditGroupCode: 1,
    userGroupEditUserList: [],
    token: cookie.load('token') || '',
  });

  const adminLogo = '/jsw_logo.png';

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

  const loadDashboard = async (overrides = {}) => {
    const nextFilter = {
      ...state.dashboardFilter,
      ...overrides,
    };
    const normalizedFilter = {
      ...nextFilter,
      grain: nextFilter.range === '24h' ? nextFilter.grain || 'hour' : 'day',
    };

    setStatePatch({
      dashboardLoading: true,
      dashboardFilter: normalizedFilter,
    });

    const client = createClient();
    const query = new URLSearchParams({
      range: normalizedFilter.range,
      grain: normalizedFilter.grain,
      userLimit: '10',
      dbLimit: '5',
      tableLimit: '10',
      recentLimit: '10',
    });
    const response = await client.get(`/api/backstage/dashboard?${query.toString()}`, {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status === true) {
      setStatePatch({
        dashboardData: response.jsonData.data,
        dashboardLoading: false,
        dashboardUpdatedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
      });
      return;
    }

    setStatePatch({
      dashboardData: null,
      dashboardLoading: false,
    });
    showDialog(response.jsonData.message || '首页统计数据加载失败');
  };

  const loadQueryLog = async (options = {}) => {
    const client = createClient();
    const query = new URLSearchParams({
      pageSize: String(options.pageSize || state.queryLogCursor.pageSize || 25),
      direction: options.direction || 'older',
    });

    if (options.cursorCode != null) {
      query.set('cursorCode', String(options.cursorCode));
    }

    const response = await client.get(`/api/backstage/querylog?${query.toString()}`, {
      headers: {
        'Content-Type': 'text/plain',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status === true) {
      setStatePatch({
        queryLogCursor: response.jsonData.data || createEmptyQueryLogCursor(),
      });
      return;
    }

    setStatePatch({
      queryLogCursor: createEmptyQueryLogCursor(),
    });
    showDialog(response.jsonData.message || '查询日志加载失败');
  };

  const loadMenu = async (menuKey) => {
    setStatePatch({
      menuSelect: menuKey,
    });

    if (menuKey === '5') {
      navigate('/');
      return;
    }

    const client = createClient();
    const headers = {
      headers: {
        'Content-Type': 'text/plain',
        'User-Token': state.token,
      },
    };

    switch (menuKey) {
      case '1': {
        await loadDashboard();
        break;
      }
      case '2': {
        const response = await client.get('/api/backstage/userlist', headers);
        setStatePatch({
          userList: response.jsonData.data,
        });
        break;
      }
      case '3': {
        const response = await client.get('/api/backstage/connlist', headers);
        setStatePatch({
          connList: response.jsonData.data,
        });
        break;
      }
      case '4': {
        await loadQueryLog();
        break;
      }
      case '6': {
        const [groupResponse, userResponse] = await Promise.all([
          client.get('/api/backstage/usergroups', headers),
          client.get('/api/backstage/userlist', headers),
        ]);
        setStatePatch({
          userGroupList: groupResponse.jsonData.data,
          userList: userResponse.jsonData.data,
        });
        break;
      }
      case '7': {
        const [groupResponse, connResponse, permResponse] = await Promise.all([
          client.get('/api/backstage/usergroups', headers),
          client.get('/api/backstage/connlist', headers),
          client.get('/api/backstage/db_perm', headers),
        ]);
        setStatePatch({
          userGroupList: groupResponse.jsonData.data,
          connList: connResponse.jsonData.data,
          dbPermissionList: permResponse.jsonData.data,
        });
        break;
      }
      default:
        break;
    }
  };

  useEffect(() => {
    if (!state.token) {
      navigate('/login', { replace: true });
      return;
    }

    const client = createClient();
    void client.get('/version').then((response) => {
      if (response.jsonData.status === true) {
        setStatePatch({
          version: response.jsonData.data || '',
        });
      }
    });

    void loadMenu('1');
  }, []);

  const testDbConn = async () => {
    const client = createClient();
    const response = await client.post('/api/backstage/testserver', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify(state.inputData),
    });

    if (response.jsonData.status === true) {
      showDialog('数据库连通性和账号可用性校验成功', '提示');
      return;
    }

    showDialog(response.jsonData.data, '连接失败');
  };

  const testServerRowBtn = async (serverCode) => {
    const serverData = state.connList.find((item) => item.code === serverCode);
    if (!serverData) {
      showDialog('未找到对应的服务器配置', '测试失败');
      return;
    }

    setStatePatch({
      testingServerCode: serverCode,
    });

    const hideMessage = message.loading({
      content: `正在测试 ${serverData.dbServerName} 的连通性和账号可用性...`,
      duration: 0,
    });

    try {
      const client = createClient();
      const response = await client.post(`/api/backstage/testserver/${serverCode}`, {
        headers: {
          'Content-Type': 'text/plain',
          'User-Token': state.token,
        },
      });

      if (response.jsonData.status === true) {
        showDialog(
          `${serverData.dbServerName} 连通性正常，账号凭据可用。`,
          '测试成功',
        );
        return;
      }

      showDialog(
        response.jsonData.data || response.jsonData.message || '测试失败，请检查服务器配置。',
        `测试失败 · ${serverData.dbServerName}`,
      );
    } finally {
      hideMessage();
      setStatePatch({
        testingServerCode: null,
      });
    }
  };

  const connHandleOk = async () => {
    const client = createClient();
    const url =
      state.inputData.code === undefined
        ? '/api/backstage/addserver'
        : '/api/backstage/update_server';
    const successMessage =
      state.inputData.code === undefined ? '服务器创建成功' : '服务器更新成功';
    const response = await client.post(url, {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify(state.inputData),
    });

    if (response.jsonData.status === true) {
      showDialog(successMessage);
      setStatePatch({
        configVisible: false,
        inputData: {},
      });
      await loadMenu('3');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const userHandleOk = async () => {
    const client = createClient();
    const response = await client.post('/api/backstage/adduser', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify(state.inputData),
    });

    if (response.jsonData.status === true) {
      setStatePatch({
        userAddVisible: false,
        inputData: {},
        linkVisible: true,
        issuedLinkTitle: '激活链接已生成',
        issuedLinkData: response.jsonData.data,
      });
      await loadMenu('2');
      return;
    }

    showDialog(response.jsonData.message || response.jsonData.data);
  };

  const issueUserLink = async (url, userName, successTitle) => {
    const client = createClient();
    const response = await client.post(url, {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify({ userName }),
    });

    if (response.jsonData.status === true) {
      setStatePatch({
        linkVisible: true,
        issuedLinkTitle: successTitle,
        issuedLinkData: response.jsonData.data,
      });
      await loadMenu('2');
      return;
    }

    showDialog(response.jsonData.message || response.jsonData.data);
  };

  const userGroupHandleOk = async () => {
    const client = createClient();
    const isCreate = state.inputData.code === undefined;
    const response = await client[isCreate ? 'post' : 'put'](
      isCreate ? '/api/backstage/add_usergroups' : '/api/backstage/set_group_data',
      {
        headers: {
          'Content-Type': 'application/json',
          'User-Token': state.token,
        },
        body: JSON.stringify(state.inputData),
      },
    );

    if (response.jsonData.status === true) {
      showDialog(isCreate ? '用户组创建成功' : '用户数据更新成功');
      setStatePatch({
        userGroupAddVisible: false,
        inputData: {},
      });
      await loadMenu('6');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const permissionHandleOk = async () => {
    const client = createClient();
    const response = await client.post('/api/backstage/add_permission', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify(state.inputData),
    });

    if (response.jsonData.status === true) {
      showDialog('权限添加成功');
      setStatePatch({
        permissionAddVisible: false,
        inputData: {},
      });
      await loadMenu('7');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const onInputChange = (event) => {
    updateInputData({
      [event.target.id]: event.target.value,
    });
  };

  const onSelectChange = (value) => {
    updateInputData({
      dbServerType: value,
      dbSslMode: value === 'mssql' ? state.inputData.dbSslMode || 'DEFAULT' : 'DEFAULT',
    });
  };

  const onSslModeChange = (value) => {
    updateInputData({
      dbSslMode: value,
    });
  };

  const onPortChange = (value) => {
    updateInputData({
      dbServerPort: value,
    });
  };

  const onUserGroupFromUserChange = (value) => {
    updateInputData({
      userList: value.map((item) => ({ code: item.split(':')[0] })),
    });
    setStatePatch({
      userGroupEditUserList: value,
    });
  };

  const onInputPermissionGroupChange = (value) => {
    updateInputData({
      groupCode: value,
    });
    setStatePatch({
      permissionEditGroupCode: value,
    });
  };

  const onInputPermissionServerChange = (value) => {
    updateInputData({
      serverList: value.map((item) => ({ code: item })),
    });
    setStatePatch({
      permissionEditServerList: value,
    });
  };

  const connHandleCancel = () => {
    setStatePatch({
      configVisible: false,
      userAddVisible: false,
      userGroupAddVisible: false,
      permissionAddVisible: false,
      linkVisible: false,
      issuedLinkTitle: '',
      issuedLinkData: null,
      inputData: {},
      userGroupEditUserList: [],
      permissionEditServerList: [],
    });
  };

  const serverAddBtn = () => {
    setStatePatch({
      configVisible: true,
      inputData: {
        dbGroup: 'default',
        dbSslMode: 'DEFAULT',
      },
    });
  };

  const userAddBtn = () => {
    setStatePatch({
      userAddVisible: true,
      inputData: {},
    });
  };

  const userGroupAddBtn = () => {
    setStatePatch({
      userGroupAddVisible: true,
      userGroupEditUserList: [],
      inputData: {},
    });
  };

  const permissionAddBtn = () => {
    setStatePatch({
      permissionAddVisible: true,
      permissionEditGroupCode: 1,
      permissionEditServerList: [],
      inputData: {
        groupCode: 1,
      },
    });
  };

  const dbPermissionDeleteBtn = async (groupCode) => {
    const client = createClient();
    const response = await client.delete(`/api/backstage/db_perm/${groupCode}`, {
      headers: {
        'Content-Type': 'text/plain',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status === true) {
      showDialog('数据删除成功');
      await loadMenu('7');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const dbPermissionEditBtn = async (groupCode) => {
    const client = createClient();
    const response = await client.get(`/api/backstage/server_list/${groupCode}`, {
      headers: {
        'Content-Type': 'text/plain',
        'User-Token': state.token,
      },
    });

    setStatePatch({
      permissionAddVisible: true,
      permissionEditGroupCode: groupCode,
      permissionEditServerList: response.jsonData.data.map((item) => item.code),
      inputData: {
        groupCode,
        serverList: response.jsonData.data,
      },
    });
  };

  const userDeleteBtn = async (userName) => {
    const client = createClient();
    const response = await client.post('/api/backstage/deluser', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify({ userName }),
    });

    if (response.jsonData.status === true) {
      showDialog('用户删除成功');
      await loadMenu('2');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const copyIssuedLink = () => {
    if (!state.issuedLinkData?.linkUrl) {
      showDialog('当前没有可复制的链接');
      return;
    }
    copy(state.issuedLinkData.linkUrl);
    message.success('链接已复制');
  };

  const userGroupDeleteBtn = async (groupCode) => {
    const client = createClient();
    const response = await client.delete(`/api/backstage/usergroup/${groupCode}`, {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
    });

    if (response.jsonData.status === true) {
      showDialog('用户组删除成功');
      setStatePatch({
        permissionAddVisible: false,
        inputData: {},
      });
      await loadMenu('6');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const userGroupEditBtn = async (groupCode) => {
    const client = createClient();
    const response = await client.get(`/api/backstage/group_user/${groupCode}`, {
      headers: {
        'Content-Type': 'text/plain',
        'User-Token': state.token,
      },
    });
    const groupData = state.userGroupList.find((item) => item.code === groupCode) || {};

    setStatePatch({
      userGroupAddVisible: true,
      userGroupEditGroupCode: groupCode,
      userGroupEditUserList: response.jsonData.data.map((item) => `${item.code}:${item.userName}`),
      inputData: {
        ...groupData,
        userList: response.jsonData.data,
      },
    });
  };

  const serverDeleteBtn = async (serverCode) => {
    const client = createClient();
    const response = await client.post('/api/backstage/delserver', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: serverCode,
    });

    if (response.jsonData.status === true) {
      showDialog('服务器删除成功');
      await loadMenu('3');
      return;
    }

    showDialog(response.jsonData.data);
  };

  const showEditServerBtn = (serverCode) => {
    const serverData = state.connList.find((item) => item.code === serverCode) || {};
    setStatePatch({
      inputData: { ...serverData },
      configVisible: true,
    });
  };

  const queryLogColumns = [
    { title: '查询时间', dataIndex: 'queryTime', width: 168 },
    { title: '查询者', dataIndex: 'queryName', width: 120 },
    { title: '查询者IP', dataIndex: 'queryIp' },
    { title: '实例', dataIndex: 'serverName', width: 150 },
    { title: '查询数据库', dataIndex: 'queryDatabase' },
    { title: '涉及表', dataIndex: 'targetTables', ellipsis: true, width: 240 },
    { title: '返回条数', dataIndex: 'resultRowCount', width: 110 },
    { title: '耗时(ms)', dataIndex: 'queryConsuming', width: 110 },
    { title: '查询脚本', dataIndex: 'querySqlscript', ellipsis: true },
  ];

  const connListColumns = [
    { title: '编号', dataIndex: 'code' },
    { title: '服务器名', dataIndex: 'dbServerName' },
    { title: '服务器地址', dataIndex: 'dbServerHost' },
    { title: '服务器端口', dataIndex: 'dbServerPort' },
    { title: '用户名', dataIndex: 'dbServerUsername' },
    { title: '服务器类型', dataIndex: 'dbServerType', render: (value) => getServerTypeLabel(value) },
    { title: '连接安全', dataIndex: 'dbSslMode', render: (value) => value || 'DEFAULT' },
    { title: '服务器分组', dataIndex: 'dbGroup' },
    {
      title: '操作',
      render: (text, record) => (
        <Space size="middle">
          <Button
            type="link"
            loading={state.testingServerCode === record.code}
            onClick={() => testServerRowBtn(record.code)}
          >
            测试
          </Button>
          <Button type="link" onClick={() => showEditServerBtn(record.code)}>
            编辑
          </Button>
          <Button type="link" onClick={() => serverDeleteBtn(record.code)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const druidColumns = [
    { title: '连接池名', dataIndex: 'poolName' },
    { title: '连接地址', dataIndex: 'jdbcUrl' },
    { title: '驱动类名', dataIndex: 'driverClassName' },
    {
      title: '健康状态',
      dataIndex: 'healthStatus',
      render: (value) => (value === 'UP' ? <Tag color="success">UP</Tag> : <Tag color="error">{value || 'DOWN'}</Tag>),
    },
    { title: '活跃连接数', dataIndex: 'activeConnections' },
    { title: '空闲连接数', dataIndex: 'idleConnections' },
    { title: '总连接数', dataIndex: 'totalConnections' },
    { title: '等待连接线程数', dataIndex: 'threadsAwaitingConnection' },
  ];

  const userListColumns = [
    {
      title: '编号',
      dataIndex: 'code',
      width: 82,
      render: (value) => <span className="admin-user-code">{value}</span>,
    },
    {
      title: '用户名',
      dataIndex: 'userName',
      width: 164,
      render: (value) => (
        <div className="admin-identity-cell">
          <strong>{value || '-'}</strong>
          <span>平台账号</span>
        </div>
      ),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 280,
      render: (value) => <span className="admin-email-text">{value || '-'}</span>,
    },
    {
      title: '账号状态',
      dataIndex: 'accountStatus',
      width: 132,
      render: (value) => {
        const meta = getAccountStatusMeta(value);
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '二次验证绑定',
      dataIndex: 'authStatus',
      width: 140,
      render: (value) => {
        const meta = getAuthStatusMeta(value);
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '待处理任务',
      dataIndex: 'pendingSecurityTaskType',
      width: 188,
      render: (value, record) => {
        if (!value) {
          return <Tag borderless className="admin-soft-tag">无</Tag>;
        }
        const meta = getPendingTaskMeta(value);
        return (
          <div className="admin-task-cell">
            <Tag color={meta.color}>{meta.text}</Tag>
            <span className="admin-task-time">{record.pendingSecurityTaskExpireTime || '-'}</span>
          </div>
        );
      },
    },
    {
      title: '访问令牌状态',
      dataIndex: 'accessTokenStatus',
      width: 122,
      render: (value) => {
        if (value === 'ACTIVE') {
          return <Tag color="success">有效</Tag>;
        }
        if (value === 'EXPIRED') {
          return <Tag color="warning">已过期</Tag>;
        }
        return <Tag borderless className="admin-soft-tag">未申请</Tag>;
      },
    },
    {
      title: '令牌到期时间',
      dataIndex: 'accessTokenExpireTime',
      width: 188,
      render: (value) => (
        <span className={`admin-token-expire ${value ? '' : 'empty'}`}>{value || '-'}</span>
      ),
    },
    {
      title: '操作',
      width: 360,
      render: (text, record) => (
        <Space className="admin-action-group" size={[8, 8]} wrap>
          {record.accountStatus === 'PENDING_ACTIVATION' ? (
            <Button
              className="admin-action-button"
              type="link"
              onClick={() =>
                issueUserLink(
                  '/api/backstage/reissue_activation_link',
                  record.userName,
                  '激活链接已重新生成',
                )
              }
            >
              重发激活链接
            </Button>
          ) : null}
          <Button
            className="admin-action-button"
            type="link"
            onClick={() =>
              issueUserLink(
                '/api/backstage/reset_user_password',
                record.userName,
                '密码重置链接已生成',
              )
            }
          >
            重置密码链接
          </Button>
          <Button
            className="admin-action-button"
            type="link"
            onClick={() =>
              issueUserLink(
                '/api/backstage/reset_user_otp',
                record.userName,
                'OTP重绑链接已生成',
              )
            }
          >
            重绑OTP链接
          </Button>
          <Button className="admin-action-button danger" type="link" onClick={() => userDeleteBtn(record.userName)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const userGroupListColumns = [
    { title: '编号', dataIndex: 'code' },
    { title: '组名', dataIndex: 'groupName' },
    { title: '备注', dataIndex: 'comment' },
    { title: '用户列表', dataIndex: 'userArray' },
    {
      title: '操作',
      render: (text, record) => (
        <Space size="middle">
          <Button type="link" onClick={() => userGroupEditBtn(record.code)}>
            编辑
          </Button>
          <Button type="link" onClick={() => userGroupDeleteBtn(record.code)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const dbPermissionListColumns = [
    { title: '组名', dataIndex: 'groupName' },
    { title: '服务器列表', dataIndex: 'serverList' },
    {
      title: '操作',
      render: (text, record) => (
        <Space size="middle">
          <Button type="link" onClick={() => dbPermissionEditBtn(record.groupCode)}>
            编辑
          </Button>
          <Button type="link" onClick={() => dbPermissionDeleteBtn(record.groupCode)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const normalizedUserSearchKeyword = state.userSearchKeyword.trim().toLowerCase();
  const filteredUserList =
    normalizedUserSearchKeyword === ''
      ? state.userList
      : state.userList.filter((user) =>
          `${user.userName || ''} ${user.email || ''}`
            .toLowerCase()
            .includes(normalizedUserSearchKeyword),
        );

  const menuItems = [
    { key: '1', icon: <EditOutlined />, label: '基础信息' },
    { key: '2', icon: <UserOutlined />, label: '账号管理' },
    { key: '6', icon: <TeamOutlined />, label: '用户组管理' },
    { key: '3', icon: <DatabaseOutlined />, label: '服务器管理' },
    { key: '7', icon: <LinkOutlined />, label: '权限管理' },
    { key: '4', icon: <TableOutlined />, label: '查询日志' },
    { key: '5', icon: <ConsoleSqlOutlined />, label: '返回前台' },
  ];

  const menuMeta = {
    '1': {
      kicker: 'Dashboard',
      title: '管理控制台',
      subtitle: '系统概况、查询趋势和热点对象一屏掌握',
    },
    '2': {
      kicker: 'Users',
      title: '账号管理',
      subtitle: '处理用户、激活链接、安全状态和访问令牌信息',
    },
    '3': {
      kicker: 'Servers',
      title: '服务器管理',
      subtitle: '维护数据库实例连接、分组和连通性检查',
    },
    '4': {
      kicker: 'Logs',
      title: '查询日志',
      subtitle: '回看最近查询行为、返回条数和执行耗时',
    },
    '6': {
      kicker: 'Groups',
      title: '用户组管理',
      subtitle: '管理用户组和成员归属',
    },
    '7': {
      kicker: 'Permissions',
      title: '权限管理',
      subtitle: '按组配置实例访问权限',
    },
  };

  const currentMenuMeta = menuMeta[state.menuSelect] || menuMeta['1'];

  const updateDashboardFilter = async (patch) => {
    await loadDashboard(patch);
  };

  const renderPageSection = (title, toolbar, content) => (
    <section className="admin-panel-card">
      <div className="admin-panel-head">
        <div>
          <h3>{title}</h3>
        </div>
        {toolbar ? <div className="admin-panel-actions">{toolbar}</div> : null}
      </div>
      <div className="admin-panel-body">{content}</div>
    </section>
  );

  return (
    <Layout className="admin-app-shell">
      <Sider className="admin-sider" theme="light" width={252}>
        <div className="admin-brand">
          <div className="admin-brand-mark">
            <img src={adminLogo} alt="JavaSqlWeb logo" />
          </div>
          <div className="admin-brand-copy">
            <strong className="admin-brand-wordmark">
              <span className="tone-java">Java</span>
              <span className="tone-sql">Sql</span>
              <span className="tone-web">Web</span>
            </strong>
            <span>{formatVersionLabel(state.version)}</span>
          </div>
        </div>
        <Menu
          className="admin-menu"
          items={menuItems}
          mode="inline"
          selectedKeys={[state.menuSelect]}
          theme="light"
          onClick={({ key }) => {
            void loadMenu(key);
          }}
        />
      </Sider>
      <Layout className="admin-shell">
        <Header className="admin-header">
          <div>
            <div className="admin-header-kicker">{currentMenuMeta.kicker}</div>
            <h1>{currentMenuMeta.title}</h1>
            <p>{currentMenuMeta.subtitle}</p>
          </div>
          <div className="admin-header-badge">
            <span>后台控制台</span>
            <strong>管理员</strong>
          </div>
        </Header>
        <Content className="admin-content">
          {state.menuSelect === '1' ? (
            <AdminDashboard
              data={state.dashboardData}
              filter={state.dashboardFilter}
              loading={state.dashboardLoading}
              updatedAt={state.dashboardUpdatedAt}
              onRangeChange={(value) => {
                void updateDashboardFilter({ range: value });
              }}
              onGrainChange={(value) => {
                void updateDashboardFilter({ grain: value });
              }}
              onRefresh={() => {
                void loadDashboard();
              }}
            />
          ) : null}

          {state.menuSelect === '2'
            ? renderPageSection(
                '账号管理',
                <div className="admin-toolbar">
                  <Button type="primary" onClick={userAddBtn}>
                    增加用户
                  </Button>
                  <Input
                    allowClear
                    className="admin-search-input"
                    placeholder="按用户名或邮箱搜索"
                    value={state.userSearchKeyword}
                    onChange={(event) => {
                      setStatePatch({
                        userSearchKeyword: event.target.value,
                      });
                    }}
                  />
                </div>,
                <Table
                  className="admin-users-table"
                  columns={userListColumns}
                  dataSource={filteredUserList}
                  pagination={{ pageSize: 25 }}
                  rowKey="code"
                  size="small"
                />,
              )
            : null}

          {state.menuSelect === '3'
            ? renderPageSection(
                '服务器管理',
                <Button type="primary" onClick={serverAddBtn}>
                  增加服务器
                </Button>,
                <Table
                  columns={connListColumns}
                  dataSource={state.connList}
                  pagination={{ pageSize: 25 }}
                  rowKey="code"
                  size="small"
                />,
              )
            : null}

          {state.menuSelect === '4'
            ? renderPageSection(
                '查询日志',
                <div className="admin-toolbar">
                  <Button onClick={() => {
                    void loadQueryLog();
                  }}
                  >
                    刷新最新
                  </Button>
                  <Button
                    disabled={!state.queryLogCursor.hasNewer || state.queryLogCursor.firstCode == null}
                    onClick={() => {
                      void loadQueryLog({
                        cursorCode: state.queryLogCursor.firstCode,
                        direction: 'newer',
                        pageSize: state.queryLogCursor.pageSize,
                      });
                    }}
                  >
                    更新
                  </Button>
                  <Button
                    disabled={!state.queryLogCursor.hasOlder || state.queryLogCursor.lastCode == null}
                    onClick={() => {
                      void loadQueryLog({
                        cursorCode: state.queryLogCursor.lastCode,
                        direction: 'older',
                        pageSize: state.queryLogCursor.pageSize,
                      });
                    }}
                  >
                    更多
                  </Button>
                  <span className="admin-dashboard-meta">
                    当前窗口 {state.queryLogCursor.items.length} 条
                  </span>
                </div>,
                <Table
                  columns={queryLogColumns}
                  dataSource={state.queryLogCursor.items}
                  pagination={false}
                  rowKey={(record) => `query-log-${record.code}`}
                  size="small"
                />,
              )
            : null}

          {state.menuSelect === '6'
            ? renderPageSection(
                '用户组管理',
                <Button type="primary" onClick={userGroupAddBtn}>
                  增加用户组
                </Button>,
                <Table
                  className="font_eng"
                  columns={userGroupListColumns}
                  dataSource={state.userGroupList}
                  pagination={{ pageSize: 25 }}
                  rowKey="code"
                  size="small"
                />,
              )
            : null}

          {state.menuSelect === '7'
            ? renderPageSection(
                '权限管理',
                <Button type="primary" onClick={permissionAddBtn}>
                  增加授权
                </Button>,
                <Table
                  columns={dbPermissionListColumns}
                  dataSource={state.dbPermissionList}
                  pagination={{ pageSize: 25 }}
                  rowKey={(record, index) => `perm-${record.groupCode || index}`}
                  size="small"
                />,
              )
            : null}

          <Modal
            confirmLoading={state.confirmLoading}
            open={state.userAddVisible}
            title="增加新用户"
            onCancel={connHandleCancel}
            onOk={userHandleOk}
          >
            <Form size="small" labelCol={{ span: 7 }}>
              <Form.Item label="邮箱">
                <Input id="email" onChange={onInputChange} />
              </Form.Item>
            </Form>
          </Modal>

          <Modal
            open={state.linkVisible}
            title={state.issuedLinkTitle || '链接已生成'}
            onCancel={() => {
              setStatePatch({
                linkVisible: false,
                issuedLinkTitle: '',
                issuedLinkData: null,
              });
            }}
            footer={[
              <Button key="copy" type="primary" onClick={copyIssuedLink}>
                复制链接
              </Button>,
              <Button
                key="close"
                onClick={() => {
                  setStatePatch({
                    linkVisible: false,
                    issuedLinkTitle: '',
                    issuedLinkData: null,
                  });
                }}
              >
                关闭
              </Button>,
            ]}
          >
            <Form size="small" labelCol={{ span: 6 }}>
              <Form.Item label="用户名">
                <span>{state.issuedLinkData?.userName || '-'}</span>
              </Form.Item>
              <Form.Item label="邮箱">
                <span>{state.issuedLinkData?.email || '-'}</span>
              </Form.Item>
              <Form.Item label="任务类型">
                <Tag color={getPendingTaskMeta(state.issuedLinkData?.taskType).color}>
                  {getPendingTaskMeta(state.issuedLinkData?.taskType).text}
                </Tag>
              </Form.Item>
              <Form.Item label="过期时间">
                <span>{state.issuedLinkData?.expireTime || '-'}</span>
              </Form.Item>
              <Form.Item label="激活链接">
                <Input.TextArea
                  autoSize={{ minRows: 3, maxRows: 5 }}
                  readOnly
                  value={state.issuedLinkData?.linkUrl || ''}
                />
              </Form.Item>
            </Form>
          </Modal>

          <Modal
            confirmLoading={state.confirmLoading}
            open={state.configVisible}
            title="增加新服务器"
            onCancel={connHandleCancel}
            onOk={connHandleOk}
          >
            <Form size="small" labelCol={{ span: 7 }}>
              <Form.Item label="服务器名">
                <Input
                  id="dbServerName"
                  value={state.inputData.dbServerName}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="服务器地址">
                <Input
                  id="dbServerHost"
                  value={state.inputData.dbServerHost}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="服务器端口">
                <InputNumber
                  id="dbServerPort"
                  max={65535}
                  min={1}
                  value={state.inputData.dbServerPort}
                  onChange={onPortChange}
                />
              </Form.Item>
              <Form.Item label="服务器用户名">
                <Input
                  id="dbServerUsername"
                  value={state.inputData.dbServerUsername}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="服务器密码">
                <Input.Password
                  id="dbServerPassword"
                  value={state.inputData.dbServerPassword}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="服务器类型">
                <Select
                  placeholder="Select a type"
                  value={state.inputData.dbServerType}
                  onChange={onSelectChange}
                >
                  <Select.Option value="mssql">mssql</Select.Option>
                  <Select.Option value="mysql">mysql</Select.Option>
                  <Select.Option value="mariadb">mariadb</Select.Option>
                  <Select.Option value="postgresql">pgsql / postgresql</Select.Option>
                  <Select.Option value="clickhouse">clickhouse</Select.Option>
                </Select>
              </Form.Item>
              <Form.Item label="连接安全">
                <Select
                  value={state.inputData.dbSslMode || 'DEFAULT'}
                  onChange={onSslModeChange}
                >
                  <Select.Option value="DEFAULT">DEFAULT</Select.Option>
                  <Select.Option value="DISABLE_ENCRYPTION">DISABLE_ENCRYPTION</Select.Option>
                  <Select.Option value="LEGACY_TLS">LEGACY_TLS</Select.Option>
                </Select>
              </Form.Item>
              {state.inputData.dbServerType === 'mssql' &&
              state.inputData.dbSslMode === 'LEGACY_TLS' ? (
                <Form.Item label="风险提示">
                  <Alert
                    message="LEGACY_TLS 仅适用于可信内网且无法升级的旧 SQL Server。启用后需要部署层显式放开 TLS1.0。"
                    showIcon
                    type="warning"
                  />
                </Form.Item>
              ) : null}
              <Form.Item label="服务器分组">
                <Input
                  id="dbGroup"
                  value={state.inputData.dbGroup}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="测试连接">
                <Button type="primary" onClick={testDbConn}>
                  连接...
                </Button>
              </Form.Item>
            </Form>
          </Modal>

          <Modal
            confirmLoading={state.confirmLoading}
            open={state.userGroupAddVisible}
            title="增加新用户组"
            onCancel={connHandleCancel}
            onOk={userGroupHandleOk}
          >
            <Form size="small" labelCol={{ span: 7 }}>
              <Form.Item label="组名">
                <Input
                  id="groupName"
                  value={state.inputData.groupName}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="备注">
                <Input
                  id="comment"
                  value={state.inputData.comment}
                  onChange={onInputChange}
                />
              </Form.Item>
              <Form.Item label="用户">
                <Select
                  mode="multiple"
                  placeholder="选择进入该组用户"
                  value={state.userGroupEditUserList}
                  onChange={onUserGroupFromUserChange}
                >
                  {state.userList.map((row) => (
                    <Select.Option
                      key={row.code}
                      label={row.userName}
                      value={`${row.code}:${row.userName}`}
                    >
                      {row.userName} ({row.code})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Form>
          </Modal>

          <Modal
            confirmLoading={state.confirmLoading}
            open={state.permissionAddVisible}
            title="增加授权"
            onCancel={connHandleCancel}
            onOk={permissionHandleOk}
          >
            <Form size="small" labelCol={{ span: 7 }}>
              <Form.Item label="用户组">
                <Select
                  showSearch
                  id="groupCode"
                  value={state.permissionEditGroupCode}
                  onChange={onInputPermissionGroupChange}
                >
                  {state.userGroupList.map((row) => (
                    <Select.Option key={row.code} value={row.code}>
                      {row.groupName}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item label="服务器">
                <Select
                  mode="multiple"
                  placeholder="选择进入该组用户"
                  value={state.permissionEditServerList}
                  onChange={onInputPermissionServerChange}
                >
                  {state.connList.map((row) => (
                    <Select.Option
                      key={row.code}
                      label={row.dbServerName}
                      value={row.code}
                    >
                      {row.dbServerName} ({row.code})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Form>
          </Modal>
        </Content>
        <Footer className="admin-footer">JavaSqlWeb {formatVersionLabel(state.version)}</Footer>
      </Layout>
    </Layout>
  );
}

export default Admin;
