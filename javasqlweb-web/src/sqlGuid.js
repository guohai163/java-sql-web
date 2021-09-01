
import React from "react";
import './sqlGuid.css';
import { Layout, Menu, Breadcrumb,List,Avatar,Image } from 'antd';
import sqlicon from './images/sql-svgrepo-com.svg'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { docco } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import logo from "./images/logo.svg";
const { Header, Footer, Sider, Content  } = Layout;

class SqlGuid extends React.Component {
    render(){
        const data = [
            {
                title: '查询wegame账号realid，服务器【10.14.95.14@账号】数据库【AccountDB】',
                desc:'select top 10 * from wegame_user_tb a inner join userid_account_rel b on a.user_id = b.user_id '
            },
            {
                title: 'Ant Design Title 2',
                desc:'aaawa'
            },
            {
                title: 'Ant Design Title 3',
                desc:'aasdfaa'
            },
            {
                title: 'Ant Design Title 4',
                desc:'aasdfaa'
            },
        ];
        console.log(data);
        return(
            <Layout>
                <Header className="header">
                    <Menu theme="dark" mode="horizontal" defaultSelectedKeys={['1']}>
                        <Menu.Item key="1">社区类</Menu.Item>
                    </Menu>
                </Header>
                <Content className="site-layout" style={{ padding: '0 50px', marginTop: 64 }}>
                    <List
                        itemLayout="horizontal"

                        dataSource={data}
                        renderItem={item => <List.Item><List.Item.Meta
                            avatar=<Image src={sqlicon} alt="logo" width={50} />
                            title={item.title}
                            description=<SyntaxHighlighter language="sql" style={docco}>
                                {item.desc}
                            </SyntaxHighlighter>
                        /></List.Item>}
                    />
                </Content>
            </Layout>
        )
    }

}

export default SqlGuid