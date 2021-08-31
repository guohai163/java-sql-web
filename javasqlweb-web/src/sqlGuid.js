
import React from "react";
import './sqlGuid.css';
import { Layout, Menu, Breadcrumb } from 'antd';
import 'antd/dist/antd.css';
const { Header, Footer, Sider, Content } = Layout;

class SqlGuid extends React.Component {
    render(){
        return(
            <Layout>
                <Header className="header">
                    <Menu theme="dark" mode="horizontal" defaultSelectedKeys={['1']}>
                        <Menu.Item key="1">nav 1</Menu.Item>
                        <Menu.Item key="2">nav 2</Menu.Item>
                        <Menu.Item key="3">nav 3</Menu.Item>
                    </Menu>
                </Header>
                <Content className="site-layout" style={{ padding: '0 50px', marginTop: 64 }}>
                    a
                </Content>
            </Layout>
        )
    }

}

export default SqlGuid