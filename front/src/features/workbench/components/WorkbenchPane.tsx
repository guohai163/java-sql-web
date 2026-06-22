import React from 'react';
import { Button, Empty, List, Result, Spin, Switch, Tabs } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import dot from '@/features/workbench/assets/dot.gif';
import DataDisplayFast from '@/features/workbench/components/DataDisplayFast';
import Spreadsheet from '@/features/workbench/components/Spreadsheet';
import WorkbenchDashboard from '@/features/workbench/components/WorkbenchDashboard';
import type { SelectionSnapshot, SqlEditorHandle } from '@/features/workbench/components/SqlEditor';
import { getServerTypeLabel } from '@/features/workbench/lib/serverType';

const SqlEditor = React.lazy(() => import('@/features/workbench/components/SqlEditor'));
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;

interface WorkbenchPaneProps {
  pane: any;
  historySql: string[];
  queryLoading: boolean;
  deskHeight: number;
  editorSchemaTables: Record<string, any>;
  editorServerType: string;
  editorRef: React.RefObject<SqlEditorHandle | null>;
  onContentTabChange: (paneKey: string, contentTab: string) => void;
  onDashboardRefresh: (pane: any) => void;
  onDataStyleSwitch: (checked: boolean) => void;
  onDeleteHistorySql: (sqlScript: string) => void;
  onExecuteSql: () => void;
  onExportQueryResult: (pane: any) => void;
  onHistorySqlToText: (sqlScript: string) => void;
  onSelectionChange: (snapshot: SelectionSnapshot) => void;
  onSqlChange: (paneKey: string, value: string, snapshot: SelectionSnapshot) => void;
}

function WorkbenchPane({
  pane,
  historySql,
  queryLoading,
  deskHeight,
  editorSchemaTables,
  editorServerType,
  editorRef,
  onContentTabChange,
  onDashboardRefresh,
  onDataStyleSwitch,
  onDeleteHistorySql,
  onExecuteSql,
  onExportQueryResult,
  onHistorySqlToText,
  onSelectionChange,
  onSqlChange,
}: WorkbenchPaneProps): React.JSX.Element {
  return (
    <>
      <div id="menubar">
        <div id="serverinfo" className="workbench-serverinfo">
          <img src={dot} alt="SERVERIMG" className="icon ic_s_host " />
          服务器: {pane.serverName} ({getServerTypeLabel(pane.serverType)})
          <span className={pane.database === '' ? 'hide' : 'none'}>
            &gt;&gt; <img src={dot} className="icon ic_s_db " alt="DBIMG" />
            数据库: {pane.database}
          </span>
        </div>
      </div>
      <div className="page_content workbench-page-content">
        <div id="queryboxContainer" className="workbench-query-shell">
          <fieldset id="queryboxf" className="workbench-query-card">
            <div id="queryfieldscontainer" className="workbench-query-grid">
              <div id="sqlquerycontainer" className="workbench-editor-panel">
                <div className="workbench-panel-heading">
                  <div>
                    <h3>SQL 编辑器</h3>
                    <p>支持对象补全、选中执行和快速切换库表</p>
                  </div>
                </div>
                <React.Suspense fallback={<div className="workbench-editor-loading">SQL 编辑器加载中...</div>}>
                  <SqlEditor
                    ref={editorRef}
                    onChange={(value, snapshot) => onSqlChange(pane.key, value, snapshot)}
                    onSelectionChange={onSelectionChange}
                    schemaTables={editorSchemaTables}
                    serverType={editorServerType}
                    value={pane.sql}
                  />
                </React.Suspense>
                <div className="workbench-editor-tip">
                  敲入关键字首字母后可使用 Ctrl+Space 快速补全，选中部分 SQL 时只执行选中语句。
                </div>
                <div className="workbench-editor-actions">
                  <Button id="button_submit_query" onClick={onExecuteSql} type="primary">
                    执行 SQL
                  </Button>
                </div>
              </div>
              <div id="tablefieldscontainer" className="workbench-history-panel">
                <div className="workbench-panel-heading">
                  <div>
                    <h3>历史记录</h3>
                    <p>保留最近执行的 SQL，方便回看和复用</p>
                  </div>
                </div>
                <List
                  dataSource={historySql}
                  renderItem={(item) => (
                    <List.Item className="workbench-history-item" key={item}>
                      <a className="workbench-history-link" onClick={() => onHistorySqlToText(item)}>
                        {item.length > 60 ? `${item.substring(0, 60)}...` : item}
                      </a>
                      <button
                        className="workbench-history-delete"
                        onClick={() => onDeleteHistorySql(item)}
                      >
                        删除
                      </button>
                    </List.Item>
                  )}
                />
              </div>
              <div className="clearfloat"></div>
            </div>
          </fieldset>
        </div>
        <fieldset id="queryboxfooter" className="tblFooters workbench-query-toolbar">
          <div className="workbench-query-toolbar-meta">
            <span className="workbench-render-label">结果视图</span>
            <Switch
              checked={pane.dataDisplayStyle}
              checkedChildren="新版"
              unCheckedChildren="旧版"
              onChange={onDataStyleSwitch}
            />
          </div>
          <div className="workbench-query-toolbar-actions">
            {pane.queryResult.length !== 0 ? (
              <Button onClick={() => onExportQueryResult(pane)}>导出查询结果</Button>
            ) : null}
          </div>
        </fieldset>
        <div className="workbench-lower-panel">
          <Tabs
            activeKey={pane.contentTab}
            className="workbench-content-tabs"
            items={[
              {
                key: 'query',
                label: '查询结果',
                children: queryLoading ? (
                  <div className="query_load workbench-loading">
                    <Spin indicator={antIcon} />
                    数据查询中...
                  </div>
                ) : pane.queryError ? (
                  <div className="query_load workbench-empty-state workbench-query-error-state">
                    <Result
                      status="error"
                      subTitle={pane.queryErrorDetail}
                      title={pane.queryErrorTitle}
                    />
                  </div>
                ) : pane.queryResult.length === 0 ? (
                  <div className="query_load workbench-empty-state">
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有查询结果" />
                  </div>
                ) : (
                  <div className="responsivetable workbench-result-panel">
                    <div className="workbench-panel-heading compact">
                      <div>
                        <h3>查询结果</h3>
                        <p>{pane.queryResult.length} 行数据</p>
                      </div>
                    </div>
                    <div className="container-wrap workbench-result-wrap" style={{ height: deskHeight }}>
                      {pane.dataDisplayStyle ? (
                        <Spreadsheet
                          data={pane.queryResult}
                          dataAreaRefresh={pane.dataAreaRefresh}
                          dataId={pane.key}
                        />
                      ) : (
                        <DataDisplayFast
                          data={pane.queryResult}
                          dataAreaRefresh={pane.dataAreaRefresh}
                        />
                      )}
                    </div>
                  </div>
                ),
              },
              {
                key: 'dashboard',
                label: 'Dashboard',
                children: (
                  <WorkbenchDashboard
                    data={pane.dashboardData}
                    error={pane.dashboardError}
                    loading={pane.dashboardLoading}
                    onRefresh={() => onDashboardRefresh(pane)}
                  />
                ),
              },
            ]}
            onChange={(contentTab) => onContentTabChange(pane.key, contentTab)}
          />
        </div>
      </div>
    </>
  );
}

export default WorkbenchPane;
