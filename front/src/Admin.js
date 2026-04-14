import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import cookie from 'react-cookies';
import {
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Layout,
  Menu,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
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
import logo from './images/logo.svg';
import './Admin.css';
import { createClient } from './apiClient';

const { confirm } = Modal;
const { Content, Footer, Sider } = Layout;

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

function Admin() {
  const navigate = useNavigate();
  const [state, setState] = useState({
    menuSelect: '1',
    queryLog: [],
    connList: [],
    configVisible: false,
    userAddVisible: false,
    confirmLoading: false,
    userGroupAddVisible: false,
    permissionAddVisible: false,
    druidList: [],
    userList: [],
    userGroupList: [],
    dbPermissionList: [],
    userCount: 0,
    serverCount: 0,
    inputData: {},
    permissionEditGroupCode: 1,
    permissionEditServerList: [],
    userGroupEditGroupCode: 1,
    userGroupEditUserList: [],
    token: cookie.load('token') || '',
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
        const response = await client.get('/api/backstage/druid/stat', headers);
        setStatePatch({
          druidList: response.jsonData,
        });
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
        const response = await client.get('/api/backstage/querylog', headers);
        setStatePatch({
          queryLog: response.jsonData.data,
        });
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

    void loadMenu('1');

    const client = createClient();
    void client
      .get('/api/backstage/base', {
        headers: {
          'Content-Type': 'text/plain',
          'User-Token': state.token,
        },
      })
      .then((response) => {
        setStatePatch({
          userCount: response.jsonData.data.user_count,
          serverCount: response.jsonData.data.server_count,
        });
      });
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
      showDialog('数据库连接成功', '提示');
      return;
    }

    showDialog(response.jsonData.data, '连接失败');
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
      showDialog('用户创建成功');
      setStatePatch({
        userAddVisible: false,
        inputData: {},
      });
      await loadMenu('2');
      return;
    }

    showDialog(response.jsonData.data);
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

  const unbindOtp = async (userName) => {
    const client = createClient();
    const response = await client.post('/api/backstage/unbind_opt', {
      headers: {
        'Content-Type': 'application/json',
        'User-Token': state.token,
      },
      body: JSON.stringify({ userName }),
    });

    if (response.jsonData.status === true) {
      showDialog('用户otp解绑成功');
      await loadMenu('2');
      return;
    }

    showDialog(response.jsonData.data);
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
    { title: '查询者IP', dataIndex: 'queryIp' },
    { title: '查询者', dataIndex: 'queryName' },
    { title: '查询数据库', dataIndex: 'queryDatabase' },
    { title: '查询脚本', dataIndex: 'querySqlscript' },
    { title: '查询时间', dataIndex: 'queryTime' },
  ];

  const connListColumns = [
    { title: '编号', dataIndex: 'code' },
    { title: '服务器名', dataIndex: 'dbServerName' },
    { title: '服务器地址', dataIndex: 'dbServerHost' },
    { title: '服务器端口', dataIndex: 'dbServerPort' },
    { title: '用户名', dataIndex: 'dbServerUsername' },
    { title: '服务器类型', dataIndex: 'dbServerType' },
    { title: '服务器分组', dataIndex: 'dbGroup' },
    {
      title: '操作',
      render: (text, record) => (
        <Space size="middle">
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
    { title: '连接名', dataIndex: 'Name' },
    { title: '连接地址', dataIndex: 'URL' },
    { title: '数据库类型', dataIndex: 'DbType' },
    { title: '驱动类名', dataIndex: 'DriverClassName' },
    { title: '执行数(总共)', dataIndex: 'ExecuteCount' },
    { title: '池中连接数', dataIndex: 'PoolingCount' },
  ];

  const userListColumns = [
    { title: '编号', dataIndex: 'code' },
    { title: '用户名', dataIndex: 'userName' },
    { title: '二次验证绑定', dataIndex: 'authStatus' },
    {
      title: '操作',
      render: (text, record) => (
        <Space size="middle">
          <Button type="link" onClick={() => unbindOtp(record.userName)}>
            解绑OTP
          </Button>
          <Button type="link" onClick={() => userDeleteBtn(record.userName)}>
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

  const menuItems = [
    { key: '1', icon: <EditOutlined />, label: '基础信息' },
    { key: '2', icon: <UserOutlined />, label: '账号管理' },
    { key: '6', icon: <TeamOutlined />, label: '用户组管理' },
    { key: '3', icon: <DatabaseOutlined />, label: '服务器管理' },
    { key: '7', icon: <LinkOutlined />, label: '权限管理' },
    { key: '4', icon: <TableOutlined />, label: '查询日志' },
    { key: '5', icon: <ConsoleSqlOutlined />, label: '返回前台' },
  ];

  return (
    <Layout>
      <Sider theme="light" className="left_meni">
        <div id="logo">
          <img src={logo} alt="logo" />
        </div>
        <Menu
          items={menuItems}
          mode="inline"
          selectedKeys={[state.menuSelect]}
          theme="light"
          onClick={({ key }) => {
            void loadMenu(key);
          }}
        />
      </Sider>
      <Layout>
        <Content>
          <div className={state.menuSelect === '1' ? 'right_content' : 'hide'}>
            <Row gutter={16}>
              <Col span={12}>
                <Statistic title="用户人数" value={state.userCount} />
              </Col>
              <Col span={12}>
                <Statistic title="数据库服务器数" value={state.serverCount} />
              </Col>
              <Col span={24}>
                <h4>数据库连接池详情</h4>
                <Table
                  columns={druidColumns}
                  dataSource={state.druidList}
                  rowKey={(record, index) => `druid-${record.Name || index}`}
                  size="small"
                />
              </Col>
            </Row>
          </div>
          <div className={state.menuSelect === '2' ? 'right_content' : 'hide'}>
            <Button type="primary" style={{ marginBottom: 16 }} onClick={userAddBtn}>
              增加用户
            </Button>
            <Modal
              confirmLoading={state.confirmLoading}
              open={state.userAddVisible}
              title="增加新用户"
              onCancel={connHandleCancel}
              onOk={userHandleOk}
            >
              <Form size="small" labelCol={{ span: 7 }}>
                <Form.Item label="用户名">
                  <Input id="userName" onChange={onInputChange} />
                </Form.Item>
                <Form.Item label="密码">
                  <Input.Password id="passWord" onChange={onInputChange} />
                </Form.Item>
              </Form>
            </Modal>
            <Table
              columns={userListColumns}
              dataSource={state.userList}
              pagination={{ pageSize: 25 }}
              rowKey="code"
              size="small"
            />
          </div>
          <div className={state.menuSelect === '3' ? 'right_content' : 'hide'}>
            <Button type="primary" style={{ marginBottom: 16 }} onClick={serverAddBtn}>
              增加服务器
            </Button>
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
                    <Select.Option value="postgresql">postgresql</Select.Option>
                  </Select>
                </Form.Item>
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
            <Table
              columns={connListColumns}
              dataSource={state.connList}
              pagination={{ pageSize: 25 }}
              rowKey="code"
              size="small"
            />
          </div>
          <div className={state.menuSelect === '4' ? 'div_content' : 'hide'}>
            <Table
              columns={queryLogColumns}
              dataSource={state.queryLog}
              pagination={{ pageSize: 25 }}
              rowKey={(record, index) => `query-log-${record.queryTime}-${index}`}
              size="small"
            />
          </div>
          <div className={state.menuSelect === '6' ? 'right_content' : 'hide'}>
            <Button type="primary" style={{ marginBottom: 16 }} onClick={userGroupAddBtn}>
              增加用户组
            </Button>
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
            <Table
              className="font_eng"
              columns={userGroupListColumns}
              dataSource={state.userGroupList}
              pagination={{ pageSize: 25 }}
              rowKey="code"
              size="small"
            />
          </div>
          <div className={state.menuSelect === '7' ? 'right_content' : 'hide'}>
            <Button type="primary" style={{ marginBottom: 16 }} onClick={permissionAddBtn}>
              增加授权
            </Button>
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
            <Table
              columns={dbPermissionListColumns}
              dataSource={state.dbPermissionList}
              pagination={{ pageSize: 25 }}
              rowKey={(record, index) => `perm-${record.groupCode || index}`}
              size="small"
            />
          </div>
        </Content>
        <Footer>javaSqlWeb ©2020 Created by Hai</Footer>
      </Layout>
    </Layout>
  );
}

export default Admin;
