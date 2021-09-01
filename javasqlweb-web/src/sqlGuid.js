
import React from "react";
import './sqlGuid.css';
import { Layout, Menu, Breadcrumb,List,Avatar,Image } from 'antd';
import sqlicon from './images/sql-svgrepo-com.svg'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { docco } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
const { Header, Content  } = Layout;

class SqlGuid extends React.Component {
    constructor(props) {
        super(props)
        this.state = {
            guidData: []
        }
    }
    componentDidMount() {

        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/sql/guid').then(response => {
            this.setState({
                guidData: response.jsonData.data
            })

        })
    }
    render(){
        const {guidData} = this.state;
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

                        dataSource={guidData}
                        renderItem={item => <List.Item><List.Item.Meta
                            avatar=<Image src={sqlicon} alt="logo" width={50} />
                            title={item.title + "，服务器【"+item.server+"】数据库【"+item.database+"】"}
                            description=<SyntaxHighlighter language="sql" style={docco}>
                                {item.script}
                            </SyntaxHighlighter>
                        /></List.Item>}
                    />
                </Content>
            </Layout>
        )
    }

}

export default SqlGuid