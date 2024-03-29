
import React from 'react';
import {withRouter} from "react-router-dom";
import cookie from 'react-cookies';
import 'antd/dist/antd.css';
import { Layout, Menu } from 'antd';
import logo from './images/logo.svg'
import config from "./config";
import './Admin.css';
import { Table, Space, Button, Modal, Form, Input, Select, InputNumber, Row, Col, Statistic } from 'antd';
import FetchHttpClient, { json } from 'fetch-http-client';
import {
    DatabaseOutlined,
    UserOutlined,
    TeamOutlined,
    TableOutlined,
    EditOutlined,
    LinkOutlined,
    ConsoleSqlOutlined
  } from '@ant-design/icons';
const { Footer, Sider, Content } = Layout;
const { Option } = Select;
const { confirm } = Modal;
class Admin extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            menuSelect: '',
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
            groupUserList: [],
            dbPermissionList: [],
            userCount: 0,
            serverCount: 0,
            inputData: {},
            permissionEditGroupCode: 1,
            permissionEditServerList: [],
            userGroupEditGroupCode: 1,
            userGroupEditUserList: [],
            token: cookie.load('token')
        }
    }
    componentDidMount() {
        // 首次打开加载事件
        this.menuClick({'key':'1'});
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/api/backstage/base',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
          .then(response => {
              this.setState({
                userCount: response.jsonData.data.user_count,
                serverCount: response.jsonData.data.server_count
              })
          })
    }
    menuClick(menu){
        this.setState({
            menuSelect: menu.key
        })
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        switch (menu.key) {
            case '1':
                client.get('/api/backstage/druid/stat',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                .then(response => {
                    this.setState({
                        druidList: response.jsonData
                    })
                })
                break;
            case '2':
                client.get('/api/backstage/userlist',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            userList: response.jsonData.data
                        })
                    })
                break;
            case '3':
                client.get('/api/backstage/connlist',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            connList: response.jsonData.data
                        })
                    })
                break;
            case '4':
                client.get('/api/backstage/querylog',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            queryLog: response.jsonData.data
                        })
                    })
                break;
            case '5':
                window.location.href = '/'
                break;
            case '6':
                client.get('/api/backstage/usergroups',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {

                        this.setState({
                            userGroupList: response.jsonData.data
                        })
                    })
                client.get('/api/backstage/userlist',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            userList: response.jsonData.data
                        })
                    })
                break;
            case '7':
                client.get('/api/backstage/usergroups',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            userGroupList: response.jsonData.data
                        })
                    })
                client.get('/api/backstage/connlist',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            connList: response.jsonData.data
                        })
                    })
                client.get('/api/backstage/db_perm',{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        this.setState({dbPermissionList: response.jsonData.data})
                    })
                break;
            default:
                break;
        }
    }
    testDbConn(){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/testserver',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
            body:JSON.stringify(this.state.inputData)})
            .then(response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'✅提示',
                        content: '✅数据库连接成功',
                        onOk(){},
                        onCancel(){}
                    });

                }
                else{
                    confirm({
                        title:'❌连接失败',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
    }
    connHandleOk(){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        console.log(this.state.inputData.code)
        if(undefined === this.state.inputData.code){
            client.post('/api/backstage/addserver',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
                body:JSON.stringify(this.state.inputData)})
            .then(response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'提示',
                        content: '服务器创建成功',
                        onOk(){},
                        onCancel(){}
                    });
                    this.setState({configVisible: false, inputData:{}})
                    this.menuClick({'key':'3'});
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
        }else{
            console.log(this.state.inputData)
            client.post('/api/backstage/update_server',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
                body:JSON.stringify(this.state.inputData)})
            .then(response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'提示',
                        content: '服务器更新成功',
                        onOk(){},
                        onCancel(){}
                    });
                    this.setState({configVisible: false, inputData:{}})
                    this.menuClick({'key':'3'});
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
        }

    }
    userHandleOk(){

        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/adduser',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token},
                body:JSON.stringify(this.state.inputData)})
            .then(response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'提示',
                        content: '用户创建成功',
                        onOk(){},
                        onCancel(){}
                    });
                    this.setState({userAddVisible: false, inputData:{}})
                    this.menuClick({'key':'2'});
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
    }
    userGroupHandleOk(){
        console.log(this.state.inputData)
        console.log(this.state.inputData['groupCode'])
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        if(undefined === this.state.inputData.code){
            // 走创建新用户组流程
            client.post('/api/backstage/add_usergroups',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token},
            body:JSON.stringify(this.state.inputData)})
                .then(response => {
                    if(true === response.jsonData.status){
                        confirm({
                            title:'提示',
                            content: '用户创建成功',
                            onOk(){},
                            onCancel(){}
                        });
                        this.setState({userGroupAddVisible: false, inputData:{}})
                        this.menuClick({'key':'6'});
                    }
                    else{
                        confirm({
                            title:'提示',
                            content: response.jsonData.data,
                            onOk(){                        },
                            onCancel(){                        }
                        });
                    }
                })
        }
        else{
            // 走编辑用户组流程
            client.put('/api/backstage/set_group_data',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token},
            body:JSON.stringify(this.state.inputData)})
                .then(response => {
                    if(true === response.jsonData.status){
                        confirm({
                            title:'提示',
                            content: '用户数据更新成功',
                            onOk(){},
                            onCancel(){}
                        });
                        this.setState({userGroupAddVisible: false, inputData:{}})
                        this.menuClick({'key':'6'});
                    }
                    else{
                        confirm({
                            title:'提示',
                            content: response.jsonData.data,
                            onOk(){                        },
                            onCancel(){                        }
                        });
                    }
                })
        }

    }
    permissionHandleOk() {
        console.log(this.state.inputData)
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/add_permission',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token},
        body:JSON.stringify(this.state.inputData)})
        .then(response => {
            if(true === response.jsonData.status){
                confirm({
                    title:'提示',
                    content: '权限添加成功',
                    onOk(){},
                    onCancel(){}
                });
                this.setState({permissionAddVisible: false, inputData:{}})
                this.menuClick({'key':'7'});
            }
            else{
                confirm({
                    title:'提示',
                    content: response.jsonData.data,
                    onOk(){                        },
                    onCancel(){                        }
                });
            }
        })
    }
    onInputChange(e){
        let data = this.state.inputData;
        data[e.target.id] = e.target.value
        this.setState({
            inputData: data
        })
    }
    onSelectChange(value){
        let data = this.state.inputData;
        data['dbServerType'] = value
        this.setState({
            inputData: data
        })
    }
    onPortChange(value){
        let data = this.state.inputData;
        data['dbServerPort'] = value
        this.setState({
            inputData: data
        })
    }
    onUserGroupFromUserChange(value){
        let data = this.state.inputData;
        data['userList'] = value.map((x) => { return {'code':x.split(':')[0]}});
        this.setState({
            inputData: data,
            userGroupEdituserList: value
        })
    }
    onInputPermissionGroupChange(value){
        console.log(value)
        let data = this.state.inputData;
        data['groupCode'] = value;
        this.setState({
            inputData: data,
            permissionEditGroupCode: value
        })
    }
    onInputPermissionServerChange(value){
        let data = this.state.inputData;
        data['serverList'] = value.map((x) => { return {'code':x}});
        this.setState({
            inputData: data,
            permissionEditServerList: value
        })
    }
    connHandleCancel(){
        this.setState({
            configVisible: false,
            userAddVisible: false,
            userGroupAddVisible: false,
            permissionAddVisible: false,
            inputData: {}
        })
    }
    serverAddBtn(){
        let data = this.state.inputData;
        data['dbGroup'] = 'default'
        this.setState({
            configVisible: true,
            inputData: data
        })
    }
    userAddBtn() {
        this.setState({
            userAddVisible: true
        })
    }
    userGroupAddBtn() {

        this.setState({
            userGroupAddVisible: true
        })
    }
    permissionAddBtn(){
        let data = this.state.inputData
        data['groupCode'] = 1
        this.setState({
            permissionAddVisible: true,
            permissionEditGroupCode: 1,
            permissionEditServerList: [],
            inputData: data
        })
    }
    dbPermissionDeleteBtn(groupCode){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.delete('/api/backstage/db_perm/'+groupCode,{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        if(true === response.jsonData.status){
                            confirm({
                                title:'提示',
                                content: '数据删除成功',
                                onOk(){},
                                onCancel(){}
                            });
                            this.menuClick({'key':'6'});
                        }
                        else{
                            confirm({
                                title:'提示',
                                content: response.jsonData.data,
                                onOk(){                        },
                                onCancel(){                        }
                            });
                        }
                    })
    }
    dbPermissionEditBtn(groupCode){
        console.log(groupCode)
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/api/backstage/server_list/'+groupCode,{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        var serverList = response.jsonData.data.map((x) => { return x.code});
                        console.log(serverList)
                        let data = this.state.inputData;
                        data['serverList'] = response.jsonData.data;
                        data['groupCode'] = groupCode;
                        this.setState({
                            permissionAddVisible: true,
                            permissionEditServerList: serverList,
                            inputData: data
                        })
                    })
    }
    userDeleteBtn(e){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/deluser',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
                body:JSON.stringify({userName: e})})
            .then(response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'提示',
                        content: '用户删除成功',
                        onOk(){},
                        onCancel(){}
                    });
                    this.menuClick({'key':'2'});
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
    }
    unbindOtp(user){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/unbind_opt',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
                body:JSON.stringify({userName: user})})
            .then(response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'提示',
                        content: '用户otp解绑成功',
                        onOk(){},
                        onCancel(){}
                    });
                    this.menuClick({'key':'2'});
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
    }
    userGroupDeleteBtn(groupCode){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.delete('/api/backstage/usergroup/'+groupCode,{headers: { 'Content-Type': 'application/json','User-Token': this.state.token}})
        .then(response => {
            if(true === response.jsonData.status){
                confirm({
                    title:'提示',
                    content: '用户组删除成功',
                    onOk(){},
                    onCancel(){}
                });
                this.setState({permissionAddVisible: false, inputData:{}})
                this.menuClick({'key':'6'});
            }
            else{
                confirm({
                    title:'提示',
                    content: response.jsonData.data,
                    onOk(){                        },
                    onCancel(){                        }
                });
            }
        })
    }
    userGroupEditBtn(groupCode){
        console.log(groupCode)
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/api/backstage/group_user/'+groupCode,{headers:{'Content-Type': 'text/plain','User-Token': this.state.token}})
                    .then(response => {
                        var userList = response.jsonData.data.map((x) => { return x.code});
                        console.log(userList)
                        let data = this.state.userGroupList.filter(item => item.code === groupCode)[0];
                        data['userList'] = response.jsonData.data;
                        this.setState({
                            userGroupAddVisible: true,
                            userGroupEditGroupCode: groupCode,
                            userGroupEdituserList: userList,
                            inputData: data
                        })
                    })
        
    }
    serverDeleteBtn(serverCode){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/delserver',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
                body:serverCode})
            .then( response => {
                if(true === response.jsonData.status){
                    confirm({
                        title:'提示',
                        content: '服务器删除成功',
                        onOk(){},
                        onCancel(){}
                    });
                    this.menuClick({'key':'3'});
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.data,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
    }
    showEditServerBtn(serverCode){
        console.log(serverCode)
        console.log(this.state.connList)
        console.log(this.state.connList.filter(item => item.code === serverCode)[0])
        this.setState({
            inputData: this.state.connList.filter(item => item.code === serverCode)[0],
            configVisible: true
        })
    }
    render(){
        const queryLogColumns = [{title:'查询者IP', dataIndex:'queryIp'},{title:'查询者', dataIndex:'queryName'},{title:'查询数据库',dataIndex:'queryDatabase'},{title:'查询脚本', dataIndex:'querySqlscript'},
                                    {title:'查询时间', dataIndex:'queryTime'}];
        const connListColumns = [{title:'编号', dataIndex:'code'},
                                {title:'服务器名', dataIndex:'dbServerName'},
                                {title:'服务器地址', dataIndex:'dbServerHost'},
                                {title:'服务器端口', dataIndex:'dbServerPort'},
                                {title:'用户名', dataIndex:'dbServerUsername'},
                                {title:'服务器类型', dataIndex:'dbServerType'},
                                {title:'服务器分组', dataIndex:'dbGroup'},
                                // {title:'创建时间', dataIndex:'createTime'},
                                {title:'操作', render: (text, record) => (<Space size="middle">
                                    <Button type="link" onClick={this.showEditServerBtn.bind(this,record.code)}>编辑</Button>
                                    <Button type="link" onClick={this.serverDeleteBtn.bind(this,record.code)}>删除</Button>
                                    </Space>)}];
        const druidColumns = [{title: '连接名', dataIndex:'Name'},
                                {title: '连接地址', dataIndex:'URL'},
                              {title: '数据库类型', dataIndex:'DbType'},
                              {title: '驱动类名', dataIndex:'DriverClassName'},
                              {title: '执行数(总共)', dataIndex:'ExecuteCount'},
                              {title: '池中连接数', dataIndex:'PoolingCount'}];
        const userListColumns = [{title:'编号', dataIndex: 'code'},
                                {title:'用户名',dataIndex: 'userName'},
                                {title:'二次验证绑定',dataIndex: 'authStatus'},
                                {title:'操作', render: (text, record) => (<Space size="middle">
                                <Button type="link" onClick={this.unbindOtp.bind(this,record.userName)}>解绑OTP</Button>
                                    <Button type="link" onClick={this.userDeleteBtn.bind(this,record.userName)}>删除</Button>
                                </Space>)}]
        const userGroupListColumns = [{title: '编号', dataIndex: 'code'},
                                        {title: '组名', dataIndex: 'groupName'},
                                        {title: '备注', dataIndex: 'comment'},
                                        {title: '用户列表', dataIndex: 'userArray'},
                                        {title:'操作', render: (text, record) => (<Space size="middle">
                                        <Button type="link" onClick={this.userGroupEditBtn.bind(this,record.code)}>编辑</Button>
                                    <Button type="link" onClick={this.userGroupDeleteBtn.bind(this,record.code)}>删除</Button>
                                    </Space>)}]
        const dbPermissionListColumns = [{title: '组名', dataIndex: 'groupName'},
                                        {title: '服务器列表', dataIndex: 'serverList'},
                                        {title:'操作', render: (text, record) => (<Space size="middle">
                                        <Button type="link" onClick={this.dbPermissionEditBtn.bind(this,record.groupCode)}>编辑</Button>
                                    <Button type="link" onClick={this.dbPermissionDeleteBtn.bind(this,record.groupCode)}>删除</Button>
                                    </Space>)}]
        let {configVisible,confirmLoading,userAddVisible, userGroupAddVisible,userList,permissionAddVisible,userGroupList,connList,dbPermissionList,
            userGroupEdituserList} = this.state;
        return (
            <>
                <Layout>
                <Sider theme="light" className="left_meni">
                    <div id="logo">
                                <img src={logo} alt="logo" />
                    </div>
                    <Menu defaultSelectedKeys="1" theme="Light" mode="inline" onClick={this.menuClick.bind(this)}>

                        <Menu.Item key="1" icon={<EditOutlined />}>
                        基础信息
                        </Menu.Item>
                        <Menu.Item key="2" icon={<UserOutlined />}>
                        账号管理
                        </Menu.Item>
                        <Menu.Item key="6" icon={<TeamOutlined />}>
                        用户组管理
                        </Menu.Item>
                        <Menu.Item key="3" icon={<DatabaseOutlined />}>
                        服务器管理
                        </Menu.Item>
                        <Menu.Item key="7" icon={<LinkOutlined />}>
                        权限管理
                        </Menu.Item>
                        <Menu.Item key="4" icon={<TableOutlined />}>
                        查询日志
                        </Menu.Item>
                        <Menu.Item key="5" icon={<ConsoleSqlOutlined />}>
                        返回前台
                        </Menu.Item>
                    </Menu>
                </Sider>
                <Layout>
                    {/* <Header>Header</Header> */}
                    <Content>
                    <div className={this.state.menuSelect === '1' ?'right_content':'hide'}>
                        <Row gutter={16}>
                            <Col span={12}>
                            <Statistic title="用户人数" value={this.state.userCount} />
                            </Col>
                            <Col span={12}>
                            <Statistic title="数据库服务器数" value={this.state.serverCount} />

                            </Col>
                            <Col span={24}>
                                <h4>数据库连接池详情</h4>
                                <Table  columns={druidColumns} dataSource={this.state.druidList} size="small" />
                            </Col>
                        </Row>
                        
                    </div>
                    <div className={this.state.menuSelect === '2' ?'right_content':'hide'}>
                        <Button onClick={this.userAddBtn.bind(this)} type="primary" style={{ marginBottom: 16 }}>
                        增加用户
                        </Button>
                        <Modal
                        title="增加新用户"
                        visible={userAddVisible}
                        onOk={this.userHandleOk.bind(this)}
                        confirmLoading={confirmLoading}
                        onCancel={this.connHandleCancel.bind(this)}
                        >
                        <Form size="small" labelCol={{ span: 7 }}>
                        <Form.Item label="用户名">
                            <Input onChange={this.onInputChange.bind(this)} id="userName"/>
                        </Form.Item>
                        <Form.Item label="密码">
                            <Input.Password onChange={this.onInputChange.bind(this)} id="passWord"/>
                        </Form.Item>
                        </Form>
                        </Modal>
                        <Table columns={userListColumns} dataSource={this.state.userList} pagination={{ pageSize: 25 }} size="small" />
                    </div>
                    <div className={this.state.menuSelect === '3' ?'right_content':'hide'}>
                    <Button onClick={this.serverAddBtn.bind(this)} type="primary" style={{ marginBottom: 16 }}>
                    增加服务器
                    </Button>
                    <Modal
                        title="增加新服务器"
                        visible={configVisible}
                        onOk={this.connHandleOk.bind(this)}
                        confirmLoading={confirmLoading}
                        onCancel={this.connHandleCancel.bind(this)}
                    >
                        <Form size="small" labelCol={{ span: 7 }}>
                            <Form.Item label="服务器名">
                                <Input onChange={this.onInputChange.bind(this)} id="dbServerName" value={this.state.inputData.dbServerName}/>
                            </Form.Item>
                            <Form.Item label="服务器地址">
                                <Input onChange={this.onInputChange.bind(this)} id="dbServerHost" value={this.state.inputData.dbServerHost}/>
                            </Form.Item>
                            <Form.Item label="服务器端口">
                                <InputNumber min={1} max={65535} onChange={this.onPortChange.bind(this)} id="dbServerPort" value={this.state.inputData.dbServerPort}/>
                            </Form.Item>
                            <Form.Item label="服务器用户名">
                                <Input onChange={this.onInputChange.bind(this)} id="dbServerUsername" value={this.state.inputData.dbServerUsername}/>
                            </Form.Item>
                            <Form.Item label="服务器密码">
                                <Input.Password onChange={this.onInputChange.bind(this)} id="dbServerPassword" value={this.state.inputData.dbServerPassword}/>
                            </Form.Item>
                            <Form.Item label="服务器类型">
                            <Select onChange={this.onSelectChange.bind(this)} value={this.state.inputData.dbServerType} placeholder="Select a type">
                                <Select.Option value="mssql">mssql</Select.Option>
                                <Select.Option value="mysql">mysql</Select.Option>
                                <Select.Option value="postgresql">postgresql</Select.Option>
                            </Select>
                            </Form.Item>
                            <Form.Item label="服务器分组">
                                <Input onChange={this.onInputChange.bind(this)} id="dbGroup" defaultValue="default" value={this.state.inputData.dbGroup}></Input>
                            </Form.Item>
                            <Form.Item label="测试连接">
                                <Button type="primary" onClick={this.testDbConn.bind(this)}>连接...</Button>
                            </Form.Item>
                        </Form>
                    </Modal>
                    <Table columns={connListColumns} dataSource={this.state.connList} pagination={{ pageSize: 25 }} size="small" />
                    </div>
                    <div className={this.state.menuSelect === '4' ?'div_content':'hide'}> 
                    <Table columns={queryLogColumns} dataSource={this.state.queryLog} pagination={{ pageSize: 25 }} size="small" />
                    </div>
                    <div className={this.state.menuSelect === '6' ?'right_content':'hide'}>
                        <Button onClick={this.userGroupAddBtn.bind(this)} type="primary" style={{ marginBottom: 16 }}>
                        增加用户组
                        </Button>
                        <Modal
                            title="增加新用户组"
                            visible={userGroupAddVisible}
                            onOk={this.userGroupHandleOk.bind(this)}
                            confirmLoading={confirmLoading}
                            onCancel={this.connHandleCancel.bind(this)}
                            >
                            <Form size="small" labelCol={{ span: 7 }}>
                            <Form.Item label="组名">
                                <Input onChange={this.onInputChange.bind(this)} id="groupName" value={this.state.inputData.groupName}/>
                            </Form.Item>
                            <Form.Item label="备注">
                                <Input onChange={this.onInputChange.bind(this)} id="comment" value={this.state.inputData.comment}/>
                            </Form.Item>
                            <Form.Item label="用户">
                                <Select mode="multiple" placeholder="选择进入该组用户" onChange={this.onUserGroupFromUserChange.bind(this)} value={userGroupEdituserList}>
                                {userList.map( row => {
                                    return(<Option value={row.code+':'+row.userName} label={row.userName}>
                                            <div className="demo-option-label-item">
                                            👤 {row.userName} ({row.code})
                                            </div>
                                            </Option>)
                                })}

                                </Select>
                            </Form.Item>
                            </Form>
                        </Modal>

                        <Table className="font_eng" columns={userGroupListColumns} dataSource={this.state.userGroupList} pagination={{ pageSize: 25 }} size="small" />

                    </div>
                    
                    <div className={this.state.menuSelect === '7' ?'right_content':'hide'}>
                        <Button onClick={this.permissionAddBtn.bind(this)} type="primary" style={{ marginBottom: 16 }}>
                        增加授权
                        </Button>

                        <Modal
                            title="增加授权"
                            visible={permissionAddVisible}
                            onOk={this.permissionHandleOk.bind(this)}
                            confirmLoading={confirmLoading}
                            onCancel={this.connHandleCancel.bind(this)}
                        >
                        <Form size="small" labelCol={{ span: 7 }}>
                        <Form.Item label="用户组">
                            <Select showSearch onChange={this.onInputPermissionGroupChange.bind(this)} id="groupCode" value={this.state.permissionEditGroupCode}>
                            {userGroupList.map(row => {
                                return(<Option value={row.code}>👥 {row.groupName}</Option>)
                            })}
                            </Select>
                        </Form.Item>
                        <Form.Item label="服务器">
                            <Select mode="multiple" placeholder="选择进入该组用户" onChange={this.onInputPermissionServerChange.bind(this)} value={this.state.permissionEditServerList}>
                            {connList.map( row => {
                                return(<Option value={row.code} label={row.dbServerName}>
                                        <div className="demo-option-label-item">
                                        🖥 {row.dbServerName} ({row.code})
                                        </div>
                                        </Option>)
                            })}
                            
                            </Select>
                        </Form.Item>
                        </Form>
                        </Modal>
                        <Table columns={dbPermissionListColumns} dataSource={dbPermissionList} pagination={{pageSize: 25 }} size="small"></Table>
                    </div>
                    </Content>
                    <Footer>javaSqlWeb ©2020 Created by Hai</Footer>
                </Layout>
                </Layout>
            </>
            
        )
    }
}
export default withRouter(Admin);