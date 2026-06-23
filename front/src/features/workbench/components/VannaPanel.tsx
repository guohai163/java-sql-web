import React from 'react';
import { Alert, Button, Empty, Input, List, Space, Spin, Tag } from 'antd';
import type { VannaGenerateSqlResponse } from '@/types/api';

interface VannaPanelProps {
  loading: boolean;
  question: string;
  response: VannaGenerateSqlResponse | null;
  error: string;
  disabled: boolean;
  onChangeQuestion: (value: string) => void;
  onSubmit: () => void;
  onCopySql: (sql: string) => void;
  onInsertSql: (sql: string) => void;
}

function VannaPanel({
  loading,
  question,
  response,
  error,
  disabled,
  onChangeQuestion,
  onSubmit,
  onCopySql,
  onInsertSql,
}: VannaPanelProps): React.JSX.Element {
  return (
    <div className="vanna-panel">
      <div className="workbench-panel-heading compact">
        <div>
          <h3>AI 问数</h3>
          <p>基于当前已选库的备注与历史查询模式，生成只读 SQL 建议</p>
        </div>
      </div>
      <Input.TextArea
        disabled={disabled}
        placeholder={disabled ? '请先在左侧选择服务器和数据库' : '例如：帮我查最近 7 天订单量前 10 的城市'}
        rows={5}
        value={question}
        onChange={(event) => onChangeQuestion(event.target.value)}
      />
      <div className="vanna-actions">
        <Button disabled={disabled || !question.trim()} loading={loading} type="primary" onClick={onSubmit}>
          生成 SQL
        </Button>
      </div>
      {loading ? (
        <div className="vanna-loading">
          <Spin />
        </div>
      ) : null}
      {error ? <Alert className="vanna-alert" message={error} showIcon type="error" /> : null}
      {!loading && !error && !response ? (
        <div className="vanna-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="输入问题后即可生成 SQL 建议" />
        </div>
      ) : null}
      {response ? (
        <div className="vanna-result">
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Alert
              message={response.summary}
              description={`方言: ${response.dialect}`}
              showIcon
              type={response.needsClarification ? 'warning' : 'success'}
            />
            {response.needsClarification ? (
              <Alert
                message="需要补充信息"
                description={response.clarificationQuestion || '请进一步明确查询条件'}
                showIcon
                type="warning"
              />
            ) : null}
            {response.sql ? (
              <div className="vanna-sql-card">
                <pre>{response.sql}</pre>
                <div className="vanna-actions">
                  <Button onClick={() => onCopySql(response.sql || '')}>复制 SQL</Button>
                  <Button type="primary" onClick={() => onInsertSql(response.sql || '')}>插入编辑器</Button>
                </div>
              </div>
            ) : null}
            {response.matchedTables.length > 0 ? (
              <div>
                <div className="vanna-subtitle">命中对象</div>
                <List
                  dataSource={response.matchedTables}
                  renderItem={(item) => (
                    <List.Item>
                      <Tag color="blue">{item}</Tag>
                    </List.Item>
                  )}
                />
              </div>
            ) : null}
            {response.warnings.length > 0 ? (
              <div>
                <div className="vanna-subtitle">提示</div>
                <List
                  dataSource={response.warnings}
                  renderItem={(item) => <List.Item>{item}</List.Item>}
                />
              </div>
            ) : null}
          </Space>
        </div>
      ) : null}
    </div>
  );
}

export default VannaPanel;
