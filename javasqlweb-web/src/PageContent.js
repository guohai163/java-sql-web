import React from 'react';
import Pubsub from 'pubsub-js'
import dot from './images/dot.gif'
import './PageContent.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import { CSVLink } from "react-csv";
import { Modal, Spin, Empty, List, Switch, Tabs } from 'antd';
import {getArray, editArray} from './ArrayOperation';
import cookie from 'react-cookies';
import { LoadingOutlined } from '@ant-design/icons';

import {Controlled as CodeMirror} from 'react-codemirror2';
import 'codemirror/lib/codemirror.css';
import "codemirror/mode/sql/sql";
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/hint/sql-hint.js';
import 'codemirror/theme/idea.css';
import 'antd/dist/antd.css';
import Spreadsheet from "./Spreadsheet";
import DataDisplayFast from "./DataDisplayFast";

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;
const { TabPane } = Tabs;

// 初始化
const initialPanes = [
    {title: 'MainTab', closable: false, key: 'Tab0', serverName: '', serverType: '', database: '', sql: '',
        queryResult: [], dataAreaRefresh: []}
]

const client = new FetchHttpClient(config.serverDomain);
client.addMiddleware(json());

class PageContent extends React.Component {
    newTabIndex = 2;
    constructor(props){
        super(props)
        this.state = {
            selectServer: '0',
            selectDatabase: '',
            selectTable: '',
            tableColumns: [],
            sql: '',
            queryResult: [],
            spName: '',
            selectServerName: '',
            selectServerType: '',
            token: cookie.load('token'),
            queryLoading: false,
            sqlValue: '',
            selectedSql: '',
            historySql: [],
            beforeSql: '',
            rearSql: '',
            dataAreaRefresh: [],
            dataDisplayStyle: true,
            options: {
                lineNumbers: true,                     //显示行号
                mode: {name: "text/x-mysql"},          //定义mode
                extraKeys: {"Ctrl": "autocomplete"},   //自动提示配置
                theme: "idea"                  //选中的theme
            },
            activeKey: initialPanes[0].key,
            panes: initialPanes,
        }
    }

    componentDidMount() {
        
        Pubsub.subscribe('dataSelect', (msg, data) => {
            if('table' === data.type){

                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
                    let sql = '';
                    if('mssql' === response.jsonData.data.dbServerType || 'mssql_druid' === response.jsonData.data.dbServerType){
                        sql = 'SELECT top 100 * FROM [' + data.selectTable +']';
                    }else if('mysql' === response.jsonData.data.dbServerType){
                        sql = 'SELECT * FROM `'+data.selectDatabase+'`.`'+data.selectTable + '` limit 100';
                    }
                    let pane = getArray(this.state.panes, this.state.activeKey)

                    pane.sql = sql;
                    pane.server = data.selectServer;
                    pane.serverName = response.jsonData.data.dbServerName;
                    pane.serverType = response.jsonData.data.dbServerType;
                    pane.database = data.selectDatabase;
                    let panes = editArray(this.state.panes, this.state.activeKey, pane);
                    this.setState({
                        selectServer: data.selectServer,
                        selectDatabase: data.selectDatabase,
                        selectTable: data.selectTable,
                        selectServerName: response.jsonData.data.dbServerName,
                        selectServerType: response.jsonData.data.dbServerType,
                        sql: sql,
                        panes: panes,
                        historySql: localStorage.getItem(data.selectServer+'_history_sql')===null?[]:JSON.parse(localStorage.getItem(data.selectServer+'_history_sql'))
                    })
                    // client.get('/database/columnslist/'+data.selectServer+'/'+data.selectDatabase+'/'+data.selectTable,
                    //             {headers:{'User-Token': this.state.token}})
                    //     .then(response => {
                    //         if(response.jsonData.status) {
                    //             this.setState({
                    //                 tableColumns: response.jsonData.data
                    //
                    //             })
                    //         }
                    //     })
                })

            }
            else if('tableName' === data.type) {
                let sql = this.state.beforeSql + ' ' + data.selectTable + ' ' +this.state.rearSql;
                let pane = getArray(this.state.panes, this.state.activeKey)
                pane.sql = sql;
                let panes = editArray(this.state.panes, this.state.activeKey, pane);
                this.setState({sql: sql, panes: panes})
            }
            else if('column' === data.type) {
                let sql = this.state.beforeSql + ' ' + data.selectColumn + ' ' +this.state.rearSql;
                let pane = getArray(this.state.panes, this.state.activeKey)
                pane.sql = sql;
                let panes = editArray(this.state.panes, this.state.activeKey, pane);
                this.setState({sql: sql, panes: panes})
            }
            else if('sp' === data.type){
                this.setState({
                    selectServer: data.selectServer,
                    selectDatabase: data.selectDatabase,
                    spName: data.spName
                })

                client.get('/database/storedprocedures/'+data.selectServer+'/'+data.selectDatabase+'/'+data.spName,
                            {headers:{'User-Token': this.state.token}})
                    .then(response => {
                        let pane = getArray(this.state.panes, this.state.activeKey)
                        pane.sql = response.jsonData.data.procedureData;
                        pane.server = data.selectServer;
                        pane.database = data.selectDatabase;
                        let panes = editArray(this.state.panes, this.state.activeKey, pane);
                        this.setState({
                            sql: response.jsonData.data.procedureData,
                            panes: panes
                        })
                    })
                
            }
            else if('view' === data.type){
                let pane = getArray(this.state.panes, this.state.activeKey)

                pane.server = data.selectServer;
                pane.database = data.selectDatabase;
                let panes = editArray(this.state.panes, this.state.activeKey, pane);
                this.setState({
                    selectServer: data.selectServer,
                    selectDatabase: data.selectDatabase,
                    panes: panes,
                })

                client.get('/database/views/'+data.selectServer+'/'+data.selectDatabase+'/'+data.viewName,
                            {headers:{'User-Token': this.state.token}})
                    .then(response => {
                        pane.sql = response.jsonData.data.viewData;
                        this.setState({
                            sql: response.jsonData.data.viewData,
                            panes: editArray(this.state.panes, this.state.activeKey, pane),
                        })
                    })
            }
            else if('database' === data.type){

                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
                    let pane = getArray(this.state.panes, this.state.activeKey)
                    pane.server = data.selectServer;
                    pane.database = data.selectDatabase;
                    pane.serverName = data.selectServerName;
                    pane.serverType = data.selectServerType;

                    this.setState({
                        selectServer: data.selectServer,
                        selectDatabase: data.selectDatabase,
                        selectServerName: response.jsonData.data.dbServerName,
                        selectServerType: response.jsonData.data.dbServerType,
                        panes: editArray(this.state.panes, this.state.activeKey, pane),
                        historySql: localStorage.getItem(data.selectServer+'_history_sql')===null?[]:JSON.parse(localStorage.getItem(data.selectServer+'_history_sql'))
                    })
                })
                client.get('/database/tablecolumn/'+data.selectServer+'/'+data.selectDatabase,{headers:{'User-Token': this.state.token}}).then( response => {
                    console.log(response.jsonData.data)
                    this.setState({
                        options: {
                            lineNumbers: true,                     //显示行号
                            mode: {name: "text/x-mysql"},          //定义mode
                            extraKeys: {"Ctrl": "autocomplete"},   //自动提示配置
                            hintOptions:{
                                tables: response.jsonData.data
                            },
                            theme: "idea"                  //选中的theme
                        }
                    })
                })
            }
            else if('server' === data.type){

                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
                    let pane = getArray(this.state.panes, this.state.activeKey)
                    pane.serverName = response.jsonData.data.dbServerName;
                    pane.serverType = response.jsonData.data.dbServerType;
                    pane.server = data.selectServer;
                    let panes = editArray(this.state.panes, this.state.activeKey, pane);
                    this.setState({
                        selectServer: data.selectServer,
                        selectDatabase: '',
                        selectServerName: response.jsonData.data.dbServerName,
                        selectServerType: response.jsonData.data.dbServerType,
                        historySql: localStorage.getItem(data.selectServer+'_history_sql')===null?[]:JSON.parse(localStorage.getItem(data.selectServer+'_history_sql'))
                    })

                })
            }

        })
    }



    execeteSql() {
        let pane = getArray(this.state.panes, this.state.activeKey)
        let sql = ''===pane.selectedSql?pane.sql:pane.selectedSql;
        if('' === sql){
            confirm({
                title:'提示',
                content: '请输入SQL语句后再执行',
                onOk(){                        },
                onCancel(){                        }
            });
            return;
        }

        if('' === pane.database){
            confirm({
                title:'提示',
                content: '请选择数据库后再执行',
                onOk(){                        },
                onCancel(){                        }
            });
            return;
        }
        pane.queryResult = [];
        this.setState({
            queryLoading: true,
            queryResult: [],
            panes: editArray(this.state.panes, this.state.activeKey, pane)
        });

        client.post('/database/query/'+pane.server+'/'+pane.database,
        {headers:{'Content-Type': 'text/plain','User-Token': this.state.token},body: sql}).then(response => {
            this.setState({queryLoading: false});
            if(response.jsonData.status){
                if(0 === response.jsonData.data.length){
                    confirm({
                        title:'提示',
                        content: '无符合查询条件数据',
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
                if('' !== response.jsonData.message){
                    confirm({
                        title:'提示',
                        content: response.jsonData.message,
                        onOk(){},
                    });
                }
                pane.dataDisplayStyle = response.jsonData.data.length>2000?false:true;
                // TODO: 先临时只使用旧版
                pane.dataDisplayStyle = false;
                pane.queryResult = response.jsonData.data;
                pane.dataAreaRefresh = [sql];
                this.setState({
                    dataDisplayStyle: response.jsonData.data.length>2000?false:true,
                    queryResult: response.jsonData.data,
                    dataAreaRefresh: [sql],
                    panes: editArray(this.state.panes, this.state.activeKey, pane),
                })
                // 检查数组中是否有此成员
                let historySql = this.state.historySql;
                if(historySql.indexOf(sql) === -1){
                    historySql.unshift(sql)
                    localStorage.setItem(this.state.selectServer + '_history_sql', JSON.stringify(historySql));
                    this.setState({
                        historySql: historySql
                    })
                }
            }else {
                this.setState({
                    queryResult: []
                })
                confirm({
                    title:'错误',
                    content: response.jsonData.message
                });
            }

        })
    }



    handleTextareaChange(e) {
        this.setState({
            sql: e
        })
    }

    selectColumn(e){
        this.setState({
            sql: this.state.sql+ ' ' + e.target.value
        })
    }

    saveCursorValue(dom) {
        let totalLine = dom.lineCount();
        let curLine = dom.getCursor();
        let beforeCount = 0;
        let rearCount = 0;
        for(var i=0;i<totalLine;i++) {
            var lineText = dom.getLine(i);
            if(curLine.line > i){
                beforeCount += lineText.length;
            }
            else if(curLine.line < i){
                rearCount += lineText.length;
            }
            else{
                beforeCount += curLine.ch;
                rearCount += lineText.length-curLine.ch;
            }
        }
        let beforeSql = dom.getValue().substring(0,beforeCount+1);
        let rearSql = dom.getValue().substring(beforeCount+1);


        this.setState({
            beforeSql: beforeSql,
            rearSql: rearSql
        })
    }
    mouseSelected(dom) {
        this.saveCursorValue(dom)
        let sqlCursor = dom.getCursor()

        let sql = dom.getValue()
        let pane = getArray(this.state.panes, this.state.activeKey)
        pane.selectedSql = dom.getSelection()
        this.setState({
            selectedSql: dom.getSelection(),
            panes: editArray(this.state.panes, this.state.activeKey, pane)
        })
    }
    historSqlToText(sqlScript){
        this.setState({
            sql: sqlScript
        })
    }
    deleteHistorySql(sql) {
        let sqlArr = this.state.historySql;
        let pos = sqlArr.indexOf(sql)
        if( -1 !== pos ){
            sqlArr.splice(pos, 1)
            this.setState({
                historySql: sqlArr
            })
        }
    }

    dataStyleSwitch(checked){
        let pane = getArray(this.state.panes, this.state.activeKey)
        pane.dataDisplayStyle = checked;
        this.setState({
            dataDisplayStyle: checked,
            panes: editArray(this.state.panes, this.state.activeKey, pane)
        })
    }

    onTabsEdit =  (targetKey, action) => {
        this[action](targetKey);
    }
    onTabsChange = activeKey => {
        this.setState({ activeKey });
    };
    add = () => {
        const { panes } = this.state;
        const activeKey = `Tab${this.newTabIndex++}`;

        const newPanes = [...panes];
        newPanes.push({ title: 'Tab ' +activeKey, key: activeKey, sql: '', dataAreaRefresh: [], queryResult: [] });
        this.setState({
            panes: newPanes,
            activeKey,
        });
    };
    remove = targetKey => {
        const { panes, activeKey } = this.state;
        let newActiveKey = activeKey;
        let lastIndex;
        panes.forEach((pane, i) => {
            if (pane.key === targetKey) {
                lastIndex = i - 1;
            }
        });
        const newPanes = panes.filter(pane => pane.key !== targetKey);
        if (newPanes.length && newActiveKey === targetKey) {
            if (lastIndex >= 0) {
                newActiveKey = newPanes[lastIndex].key;
            } else {
                newActiveKey = newPanes[0].key;
            }
        }
        this.setState({
            panes: newPanes,
            activeKey: newActiveKey,
        });
    }
    render(){
        const {sql, queryResult, selectDatabase, panes, activeKey} = this.state;

        return (
            <div className="right_area">
                <Tabs type="editable-card" onEdit={this.onTabsEdit} onChange={this.onTabsChange} activeKey={activeKey}>
                    {panes.map(pane=>(
                        <TabPane tab={pane.title} key={pane.key} closable={pane.closable}>
                            <div id="menubar">
                                <div id="serverinfo">
                                    <img src={dot}  alt="SERVERIMG" className="icon ic_s_host "/>
                                    服务器: {pane.serverName} ({pane.serverType})
                                    <span className={'' === pane.database?'hide':'none'}>
                                    &gt;&gt; <img src={dot} className="icon ic_s_db " alt="DBIMG"/>数据库: {pane.database}
                                    </span>


                                </div>
                            </div>
                            <div className='page_content'>
                                <div id="queryboxContainer">
                                    <fieldset id="queryboxf">
                                        <div id="queryfieldscontainer">
                                            <div id="sqlquerycontainer">

                                                {/* <textarea value={sql} onChange={this.handleTextareaChange.bind(this)} tabIndex="100" name="sql_query" id="sqlquery" cols="40" rows="20">

                                                </textarea> */}
                                                <CodeMirror ref="editor" onCursorActivity={this.mouseSelected.bind(this)} value={pane.sql} onBeforeChange={(editor, data, value) => {let pane = getArray(this.state.panes, this.state.activeKey); pane.sql = value; console.log(pane);  this.setState({sql: value,panes: editArray(this.state.panes, this.state.activeKey, pane)});}}  options={this.state.options} />
                                                * 敲入关键字首字母后可以使用Ctrl进行快速补全，选中部分SQL只会执行选中部分的语句！
                                            </div>
                                            <label>历史记录</label>
                                            <div id="tablefieldscontainer">
                                                {/* <select id="tablefields" name="dummy" size="13" multiple="multiple" onChange={this.selectColumn.bind(this)}>
                                                    {this.state.tableColumns.map( column =>
                                                        <option value={column.columnName} title="">{column.columnName} - {column.columnType}({column.columnLength})</option>
                                                    )}
                                                </select> */}
                                                <List dataSource={this.state.historySql} renderItem={item => (
                                                    <List.Item key={item}><a key={item} onClick={this.historSqlToText.bind(this, item)}>{item.length>60?item.substring(0,60)+'...':item}</a><button className="btn_right" onClick={this.deleteHistorySql.bind(this, item)}>删除</button></List.Item>
                                                )}></List>

                                            </div>
                                            <div className="clearfloat"></div>
                                        </div>
                                    </fieldset>
                                </div>
                                <fieldset id="queryboxfooter" className="tblFooters">
                                    <Switch checkedChildren="新版" unCheckedChildren="旧版" defaultChecked checked={this.state.dataDisplayStyle} onChange={this.dataStyleSwitch.bind(this)} />
                                    <input className="btn btn-primary" type="submit" id="button_submit_query" name="SQL"
                                           tabIndex="200" value="执行SQL" onClick={this.execeteSql.bind(this)} />
                                    { 0 !== queryResult.length? (<CSVLink data={queryResult}>导出查询结果</CSVLink>):(<span></span>) }

                                    <div className="clearfloat"></div>
                                </fieldset>
                                <div className={this.state.queryLoading || this.state.queryResult.length === 0?'hide':'responsivetable'}>
                                    {pane.dataDisplayStyle?
                                        <Spreadsheet data={pane.queryResult} dataAreaRefresh={pane.dataAreaRefresh}></Spreadsheet>
                                        :
                                        <DataDisplayFast data={pane.queryResult} dataAreaRefresh={pane.dataAreaRefresh}></DataDisplayFast>

                                    }
                                </div>
                                <div className={this.state.queryLoading?'query_load':'hide'}>
                                    <Spin indicator={antIcon} />数据查询中...
                                </div>
                                <div className={!this.state.queryLoading && 0 === this.state.queryResult.length ?'query_load':'hide'}>
                                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
                                </div>
                            </div>
                        </TabPane>
                    ))}
                </Tabs>
            </div>
        )
    }
}

export default PageContent