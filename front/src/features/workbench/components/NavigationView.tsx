import React from 'react';
import { Button, Form, Input, Modal, Select, Spin, Tag } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import dot from '@/features/workbench/assets/dot.gif';
import config from '@/shared/config/runtimeConfig';
import { formatVersionLabel } from '@/shared/lib/version';

const antIcon = <LoadingOutlined style={{ fontSize: 24 }} spin />;
const workbenchLogo = '/jsw_logo.png';

interface NavigationViewProps {
  state: any;
  copyAccessToken: () => void;
  createAccessToken: () => void | Promise<void>;
  dbChange: (dbName: string) => void | Promise<void>;
  filterTable: (event: React.ChangeEvent<HTMLInputElement>) => void;
  formatServerLabel: (server: any) => string;
  getAccessTokenStatusMeta: (status: string | undefined) => { color: string; text: string };
  getServerList: () => void | Promise<void>;
  getSpList: (dbName: string) => void | Promise<void>;
  getViewsList: (dbName: string) => void | Promise<void>;
  jumpAdmin: () => void;
  logout: () => void;
  matchServerKeyword: (server: any, keyword: string) => boolean;
  modalHandleCancel: () => void;
  modalHandleOk: () => void | Promise<void>;
  onInputChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  passKeyBind: () => void | Promise<void>;
  renewAccessToken: () => void | Promise<void>;
  resetAccessToken: () => void | Promise<void>;
  sendColumnName: (columnName: string) => void;
  sendTableName: (tableName: string) => void;
  serverChange: (value: string) => void | Promise<void>;
  showPassModal: () => void | Promise<void>;
  showTableColumn: (tableName: string) => void | Promise<void>;
  spChange: (spName: string) => void | Promise<void>;
  tableChange: (tableName: string) => void;
  viewChange: (viewName: string) => void | Promise<void>;
}

function NavigationView({
  state,
  copyAccessToken,
  createAccessToken,
  dbChange,
  filterTable,
  formatServerLabel,
  getAccessTokenStatusMeta,
  getServerList,
  getSpList,
  getViewsList,
  jumpAdmin,
  logout,
  matchServerKeyword,
  modalHandleCancel,
  modalHandleOk,
  onInputChange,
  passKeyBind,
  renewAccessToken,
  resetAccessToken,
  sendColumnName,
  sendTableName,
  serverChange,
  showPassModal,
  showTableColumn,
  spChange,
  tableChange,
  viewChange,
}: NavigationViewProps): React.JSX.Element {
  const handleLink = (event: React.MouseEvent, action: () => void | Promise<void>) => {
    event.preventDefault();
    void action();
  };

  return (
    <div id="navigation" className="workbench-navigation">
      <div id="navigation_resizer"></div>
      <div id="navigation_content">
        <div id="navigation_header">
          <div id="logo" className="workbench-brand">
            <img src={workbenchLogo} alt="JavaSqlWeb logo" />
            <div className="workbench-brand-copy">
              <strong className="workbench-brand-wordmark">
                <span className="tone-java">Java</span>
                <span className="tone-sql">Sql</span>
                <span className="tone-web">Web</span>
              </strong>
              <span className="workbench-brand-version">{formatVersionLabel(config.version)}</span>
            </div>
          </div>
          <div id="navipanellinks" className="workbench-quick-actions">
            <a className="workbench-action-link" href="/guid" rel="noreferrer" target="_blank" title="常用SQL">
              <img src={dot} alt="常用SQL" className="icon ic_s_sqlguid" />
            </a>
            <a className="workbench-action-link" href="/" title="刷新" onClick={(event) => handleLink(event, getServerList)}>
              <img src={dot} alt="刷新" className="icon ic_s_reload" />
            </a>
            <a className="workbench-action-link" href="/" title="修改密码" onClick={(event) => handleLink(event, showPassModal)}>
              <img src={dot} alt="修改密码" className="icon ic_u_pass" />
            </a>
            <a className="workbench-action-link" href="/" title="passkey" onClick={(event) => handleLink(event, passKeyBind)}>
              <img src={dot} alt="passkey" className="icon ic_w_authn" />
            </a>
            <a
              className={config.userName === 'admin' ? 'workbench-action-link' : 'hide'}
              href="/"
              title="设置"
              onClick={(event) => handleLink(event, jumpAdmin)}
            >
              <img src={dot} alt="setting" className="icon ic_s_cog" />
            </a>
            <a className="workbench-action-link" href="/" title="退出" onClick={(event) => handleLink(event, logout)}>
              <img src={dot} alt="exit" className="icon ic_s_loggoff" />
            </a>
          </div>
        </div>
        <div id="navigation_tree">
          <div className="navigation_server">
            <label className="navigation_server_label">服务器</label>
            <Select
              placeholder="请选择服务器"
              className="workbench-server-select"
              style={{ width: '100%' }}
              value={state.selectServer === '0' ? undefined : state.selectServer}
              showSearch
              optionFilterProp="children"
              filterOption={(input, option) => matchServerKeyword(option?.server, input)}
              onChange={serverChange}
            >
              {state.dbGroup.map((group) => (
                <Select.OptGroup key={group} label={group}>
                  {state.serverList
                    .filter((item) => item.dbGroup === group)
                    .map((server) => (
                      <Select.Option key={server.code} value={server.code} server={server}>
                        {formatServerLabel(server)}
                      </Select.Option>
                    ))}
                </Select.OptGroup>
              ))}
            </Select>
          </div>
          <div id="navigation_tree_content" style={{ height: state.deskHeight }}>
            <ul>
              {state.dbList.map((db) => (
                <li className="database" key={db.dbName}>
                  <div className="block">
                    <i></i>
                    <b></b>
                    <a className="expander loaded" href="/" onClick={(event) => handleLink(event, () => dbChange(db.dbName))}>
                      <span className="hide aPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                      <span className="hide vPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                      <span className="hide pos">0</span>
                      <img
                        src={dot}
                        title="扩展/收起"
                        alt="扩展/收起"
                        className={state.selectDatabase === db.dbName ? 'icon ic_b_minus' : 'icon ic_b_plus'}
                      />
                    </a>
                  </div>
                  <div className="block">
                    <a href="/" onClick={(event) => handleLink(event, () => dbChange(db.dbName))}>
                      <img src={dot} alt="数据库操作" className="icon ic_s_db" />
                    </a>
                  </div>
                  <a className="hover_show_full" href="/" onClick={(event) => handleLink(event, () => dbChange(db.dbName))}>
                    {db.dbName}
                  </a>
                  <div className={state.tableLoading && state.selectDatabase === db.dbName ? 'clearfloat' : 'hide'}>
                    <Spin indicator={antIcon} />
                  </div>
                  <div className={state.selectDatabase === db.dbName ? 'list_container' : 'hide'}>
                    <ul>
                      <li className="filter_input">
                        <Input allowClear placeholder="Filter" size="small" onChange={filterTable} />
                      </li>
                      {state.selectDatabase !== db.dbName
                        ? null
                        : state.filterTableList.map((table) => (
                            <li className="view workbench-table-row" key={table.tableName}>
                              <div className="block">
                                <i></i>
                                <a className="expander" href="/">
                                  <span className="hide pos2_name">views</span>
                                  <span className="hide pos2_value">0</span>
                                  <img
                                    src={dot}
                                    title="扩展/收起"
                                    alt="扩展/收起"
                                    className={state.showTableColumn === table.tableName ? 'icon ic_b_minus' : 'icon ic_b_plus'}
                                    onClick={(event) => {
                                      event.preventDefault();
                                      void showTableColumn(table.tableName);
                                    }}
                                  />
                                </a>
                              </div>
                              <div className="block">
                                <a href="/">
                                  <img src={dot} title="视图" alt="视图" className="icon ic_b_props" />
                                </a>
                              </div>
                              <a
                                className="hover_show_full workbench-tree-link workbench-table-name"
                                href="/"
                                title={`${table.tableName} (${table.tableRows})`}
                                onClick={(event) => handleLink(event, () => sendTableName(table.tableName))}
                                onDoubleClick={(event) => {
                                  event.preventDefault();
                                  tableChange(table.tableName);
                                }}
                              >
                                {' '}
                                {table.tableName} ({table.tableRows})
                              </a>
                              <div className="clearfloat"></div>
                              <div className={state.showTableColumn === table.tableName ? 'list_container' : 'hide'}>
                                <ul>
                                  {state.showTableColumn !== table.tableName
                                    ? null
                                    : state.columntData.map((column) => (
                                        <li key={column.columnName} onClick={() => sendColumnName(column.columnName)}>
                                          {column.columnName}({column.columnType}
                                          {column.columnLength === '' ? '' : `(${column.columnLength})`},{column.columnIsNull})
                                          <br /> - <Tag color="green">{column.columnComment === '' ? 'NULL' : column.columnComment}</Tag>
                                        </li>
                                      ))}
                                  {state.showTableColumn !== table.tableName
                                    ? null
                                    : state.indexData.map((indexData) => (
                                        <li key={indexData.indexName}>
                                          <img src={dot} title="视图" alt="视图" className="icon ic_b_views" />
                                          {indexData.indexName}[{indexData.indexKeys}]
                                        </li>
                                      ))}
                                </ul>
                              </div>
                            </li>
                          ))}
                      <li className="view" key="procedure">
                        <a href="/" onClick={(event) => handleLink(event, () => getSpList(db.dbName))}>
                          <img src={dot} alt="toggle routines" className={state.filterSpList.length === 0 ? 'icon ic_b_plus' : 'icon ic_b_minus'} />
                          <img src={dot} title="存储过程" alt="存储过程" className="icon ic_b_routines" />
                          存储过程
                        </a>
                        <div className={state.filterSpList.length === 0 ? 'hide' : 'list_container'}>
                          <ul>
                            {state.filterSpList.map((sp) => (
                              <li className="view" key={sp.procedureName}>
                                <div className="block">
                                  <a href="/">
                                    <img src={dot} title="视图" alt="视图" className="icon ic_b_routines" />
                                  </a>
                                </div>
                                <a className="hover_show_full workbench-tree-link" href="/" title="" onClick={(event) => handleLink(event, () => spChange(sp.procedureName))}>
                                  {' '}
                                  {sp.procedureName}
                                </a>
                                <div className="clearfloat"></div>
                              </li>
                            ))}
                          </ul>
                        </div>
                      </li>
                      <li className="view" key="views">
                        <a href="/" onClick={(event) => handleLink(event, () => getViewsList(db.dbName))}>
                          <img src={dot} alt="toggle views" className={state.viewList.length === 0 ? 'icon ic_b_plus' : 'icon ic_b_minus'} />
                          <img src={dot} title="视图" alt="视图" className="icon ic_b_views" />
                          视图
                        </a>
                        <div className={state.viewList.length === 0 ? 'hide' : 'list_container'}>
                          <ul>
                            {state.viewList.map((view) => (
                              <li className="view" key={view.viewName}>
                                <div className="block">
                                  <a href="/">
                                    <img src={dot} title="视图" alt="视图" className="icon ic_b_views" />
                                  </a>
                                </div>
                                <a className="hover_show_full workbench-tree-link" href="/" title="" onClick={(event) => handleLink(event, () => viewChange(view.viewName))}>
                                  {' '}
                                  {view.viewName}
                                </a>
                                <div className="clearfloat"></div>
                              </li>
                            ))}
                          </ul>
                        </div>
                      </li>
                    </ul>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
      <Modal
        open={state.passVisible}
        title="账号安全"
        onCancel={modalHandleCancel}
        onOk={modalHandleOk}
        okText="更新密码"
        cancelText="关闭"
      >
        <Form labelCol={{ span: 7 }} size="small">
          <Form.Item label="请输入新密码" rules={[{ required: true, message: '请输入密码!' }]}>
            <Input.Password
              id="userNewPassword"
              value={state.inputData.userNewPassword || ''}
              onChange={onInputChange}
            />
          </Form.Item>
        </Form>
        <div className="security-help">
          密码至少 8 位，且需包含大写字母、小写字母、数字、特殊字符中的 3 类。
        </div>
        <div className="security-section">
          <div className="security-section-title">访问令牌</div>
          {state.accessTokenLoading ? (
            <div className="security-loading">
              <Spin indicator={antIcon} />
            </div>
          ) : (
            <>
              <div className="security-meta">
                <Tag color={getAccessTokenStatusMeta(state.accessTokenInfo?.accessTokenStatus).color}>
                  {getAccessTokenStatusMeta(state.accessTokenInfo?.accessTokenStatus).text}
                </Tag>
                <span>到期时间：{state.accessTokenInfo?.accessTokenExpireTime || '未申请'}</span>
              </div>
              {state.accessTokenInfo?.hasAccessToken ? (
                <div className="security-token-box">
                  {state.accessTokenInfo.accessTokenFullVisible
                    ? state.accessTokenInfo.accessToken
                    : state.accessTokenInfo.maskedAccessToken}
                </div>
              ) : (
                <div className="security-help">当前还没有访问令牌</div>
              )}
              {state.accessTokenInfo?.accessTokenFullVisible ? (
                <Button size="small" type="primary" onClick={copyAccessToken}>
                  复制完整令牌
                </Button>
              ) : null}
              {state.accessTokenInfo?.authStatus !== 'BIND' ? (
                <div className="security-help">需先绑定OTP才能申请或续期访问令牌</div>
              ) : null}
              <div className="security-actions">
                {state.accessTokenInfo?.canCreateAccessToken ? (
                  <Button loading={state.accessTokenActionLoading} type="primary" onClick={() => void createAccessToken()}>
                    申请访问令牌
                  </Button>
                ) : null}
                {state.accessTokenInfo?.canRenewAccessToken ? (
                  <Button loading={state.accessTokenActionLoading} onClick={() => void renewAccessToken()}>
                    续期90天
                  </Button>
                ) : null}
                {state.accessTokenInfo?.canResetAccessToken ? (
                  <Button danger loading={state.accessTokenActionLoading} onClick={() => void resetAccessToken()}>
                    重置令牌
                  </Button>
                ) : null}
              </div>
              <div className="security-help">
                接口请求时请在 Header 中传 <code>Authorization: Bearer &lt;token&gt;</code>
              </div>
              {state.accessTokenInfo?.hasAccessToken && !state.accessTokenInfo?.accessTokenFullVisible ? (
                <div className="security-help">
                  完整令牌只会在申请成功或重置成功时显示一次，请及时复制保存。
                </div>
              ) : null}
            </>
          )}
        </div>
      </Modal>
    </div>
  );
}

export default NavigationView;
