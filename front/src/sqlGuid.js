
import React from "react";
import './sqlGuid.css';
import { Layout, Menu, List, Button, message, Image } from 'antd';
import sqlicon from './images/sql-svgrepo-com.svg'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { docco } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import copy from 'copy-to-clipboard';
const { Header, Content  } = Layout;

class SqlGuid extends React.Component {
    constructor(props) {
        super(props)
        this.state = {
            guidData: [],
            menuKey: "stage"
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
    menuSwitch(menu) {
        console.log(menu.key)
        this.setState({
            menuKey: menu.key
        })
    }
    copyText(o) {
        console.log(o)
        copy(o)
        message.info('复制成功');
    }
    render(){
        const {guidData, menuKey} = this.state;
        return(
            <Layout>
                <Header className="header">
                    <Menu theme="dark" mode="horizontal" defaultSelectedKeys={['stage']} onClick={this.menuSwitch.bind(this)}>
                        <Menu.Item key="stage">社区类</Menu.Item>
                        <Menu.Item key="cospower">电池类</Menu.Item>
                    </Menu>
                </Header>
                <Content className="site-layout" style={{ padding: '0 50px', marginTop: 64 }}>
                    <List
                        itemLayout="horizontal"

                        dataSource={guidData.filter(item => item.category === menuKey)}
                        renderItem={item => <List.Item actions={[<Button size="small" type="primary" onClick={this.copyText.bind(this, item.script)}>copy</Button>]}><List.Item.Meta
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