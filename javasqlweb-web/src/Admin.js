
import React from 'react';
import {withRouter} from "react-router-dom";

import 'antd/dist/antd.css';
import { Layout, Menu } from 'antd';
import logo from './images/logo.svg'
import config from "./config";
import './Admin.css';
import { Table, Space, Button, Modal, Form, Input, Select, InputNumber } from 'antd';
import FetchHttpClient, { json } from 'fetch-http-client';
import {
    DatabaseOutlined,
    UserOutlined,
    TableOutlined,
    EditOutlined,
    ConsoleSqlOutlined
  } from '@ant-design/icons';
const { Header, Footer, Sider, Content } = Layout;

class Admin extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            menuSelect: '',
            queryLog: [],
            connList: [],
            configVisible: false,
            confirmLoading: false
        }
    }
    menuClick(menu){
        this.setState({
            menuSelect: menu.key
        })
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        if("5" === menu.key) {
            window.location.href = '/'
        }
        else if('4' === menu.key){
            client.get('/api/backstage/querylog')
            .then(response => {
                console.log(response.jsonData)
                this.setState({
                    queryLog: response.jsonData.data
                })
            })
            
        }
        else if('3' === menu.key){
            client.get('/api/backstage/connlist')
            .then(response => {
                console.log(response.jsonData)
                this.setState({
                    connList: response.jsonData.data
                })
            })
        }
    }
    connHandleOk(){
        console.log('connHandleOk')
    }
    connHandleCancel(){
        console.log('connHandleCancel')
        this.setState({
            configVisible: false
        })
    }
    serverAddBtn(){
        this.setState({
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
                                {title:'创建时间', dataIndex:'createTime'},
                                {title:'操作', render: (text, record) => (<Space size="middle"><a>Delete{record.code}</a></Space>)}];
        let {configVisible,confirmLoading} = this.state;
        return (
            <>
                <Layout>
                <Sider theme="light" className="left_meni">
                    <div id="logo">
                                <img src={logo} alt="logo" />
                    </div>
                    <Menu theme="Light" mode="inline" onClick={this.menuClick.bind(this)}>

                        <Menu.Item key="1" icon={<EditOutlined />}>
                        基础设置
                        </Menu.Item>
                        <Menu.Item key="2" icon={<UserOutlined />}>
                        账号管理
                        </Menu.Item>
                        <Menu.Item key="3" icon={<DatabaseOutlined />}>
                        服务器管理
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
                    <Header>Header</Header>
                    <Content>
                    <div className={this.state.menuSelect === '3' ?'':'hide'}>
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
                            <Input />
                        </Form.Item>
                        <Form.Item label="服务器地址">
                            <Input />
                        </Form.Item>
                        <Form.Item label="服务器端口">
                            <InputNumber min={1} max={65535} />
                        </Form.Item>
                        <Form.Item label="服务器用户名">
                            <Input />
                        </Form.Item>
                        <Form.Item label="服务器密码">
                            <Input.Password />
                        </Form.Item>
                        <Form.Item label="服务器类型">
                        <Select>
                            <Select.Option value="mssql">mssql</Select.Option>
                            <Select.Option value="mysql">mysql</Select.Option>
                        </Select>
                        </Form.Item>
                        </Form>
                    </Modal>
                    <Table columns={connListColumns} dataSource={this.state.connList} pagination={{ pageSize: 25 }} size="small" />
                    </div>
                    <div className={this.state.menuSelect === '4' ?'div_content':'hide'}> 
                    <Table columns={queryLogColumns} dataSource={this.state.queryLog} pagination={{ pageSize: 25 }} size="small" />
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