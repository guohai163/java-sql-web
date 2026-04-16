import React, { useEffect, useState } from 'react';
import { Button, Image, Layout, List, Menu, message } from 'antd';
import SyntaxHighlighter from 'react-syntax-highlighter';
import { docco } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import copy from 'copy-to-clipboard';
import { createClient } from '@/shared/api/apiClient';
import sqlicon from '@/shared/assets/icons/sql-svgrepo-com.svg';
import './SqlGuide.css';

const { Header, Content } = Layout;

function SqlGuide() {
  const [guidData, setGuidData] = useState([]);
  const [menuKey, setMenuKey] = useState('stage');

  useEffect(() => {
    const client = createClient();

    void client.get('/sql/guid').then((response) => {
      setGuidData(response.jsonData.data);
    });
  }, []);

  const copyText = (script) => {
    copy(script);
    message.success('复制成功');
  };

  const menuItems = [
    { key: 'stage', label: '社区类' },
    { key: 'cospower', label: '电池类' },
  ];

  return (
    <Layout className="sql-guide-layout">
      <Header className="header">
        <Menu
          defaultSelectedKeys={['stage']}
          items={menuItems}
          mode="horizontal"
          onClick={({ key }) => setMenuKey(key)}
          theme="dark"
        />
      </Header>
      <Content className="site-layout" style={{ padding: '0 50px', marginTop: 64 }}>
        <List
          dataSource={guidData.filter((item) => item.category === menuKey)}
          itemLayout="horizontal"
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button
                  key={`copy-${item.title}`}
                  size="small"
                  type="primary"
                  onClick={() => copyText(item.script)}
                >
                  copy
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<Image alt="sql-icon" preview={false} src={sqlicon} width={50} />}
                description={
                  <SyntaxHighlighter language="sql" style={docco}>
                    {item.script}
                  </SyntaxHighlighter>
                }
                title={`${item.title}，服务器【${item.server}】数据库【${item.database}】`}
              />
            </List.Item>
          )}
        />
      </Content>
    </Layout>
  );
}

export default SqlGuide;
