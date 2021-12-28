import React from 'react';
import Pubsub from 'pubsub-js'
import dot from './images/dot.gif'
import './PageContent.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import { CSVLink } from "react-csv";
import { Modal, Spin, Empty, List } from 'antd';
import cookie from 'react-cookies';
import { LoadingOutlined } from '@ant-design/icons';

import {Controlled as CodeMirror} from 'react-codemirror2';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/sql/sql';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/hint/sql-hint.js';
import 'codemirror/theme/idea.css';
import 'antd/dist/antd.css';
import Spreadsheet from "./Spreadsheet";

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;

const options={
    lineNumbers: true,                     //显示行号
    mode: {name: "text/x-mysql"},          //定义mode
    extraKeys: {"Ctrl": "autocomplete"},   //自动提示配置
    theme: "idea"                  //选中的theme
};  


class PageContent extends React.Component {
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
            dataAreaRefresh: []
        }
    }

    componentDidMount() {
        
        Pubsub.subscribe('dataSelect', (msg, data) => {
            if('table' === data.type){
                const client = new FetchHttpClient(config.serverDomain);
                client.addMiddleware(json());
                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
                    let sql = '';
                    if('mssql' === response.jsonData.data.dbServerType || 'mssql_druid' === response.jsonData.data.dbServerType){
                        sql = 'SELECT top 100 * FROM [' + data.selectTable +']';
                    }else if('mysql' === response.jsonData.data.dbServerType){
                        sql = 'SELECT * FROM `'+data.selectDatabase+'`.`'+data.selectTable + '` limit 100';
                    }
                    this.setState({
                        selectServer: data.selectServer,
                        selectDatabase: data.selectDatabase,
                        selectTable: data.selectTable,
                        selectServerName: response.jsonData.data.dbServerName,
                        selectServerType: response.jsonData.data.dbServerType,
                        sql: sql,
                        historySql: localStorage.getItem(data.selectServer+'_history_sql')===null?[]:JSON.parse(localStorage.getItem(data.selectServer+'_history_sql'))
                    })
                    client.get('/database/columnslist/'+data.selectServer+'/'+data.selectDatabase+'/'+data.selectTable,
                                {headers:{'User-Token': this.state.token}})
                        .then(response => {
                            if(response.jsonData.status) {
                                this.setState({
                                    tableColumns: response.jsonData.data
        
                                })
                            }
                        })
                })

            }
            else if('tableName' === data.type) {
                let sql = this.state.beforeSql + ' ' + data.selectTable + ' ' +this.state.rearSql;
                this.setState({sql: sql})
            }
            else if('column' === data.type) {
                let sql = this.state.beforeSql + ' ' + data.selectColumn + ' ' +this.state.rearSql;
                this.setState({sql: sql})
            }
            else if('sp' === data.type){
                this.setState({
                    selectServer: data.selectServer,
                    selectDatabase: data.selectDatabase,
                    spName: data.spName
                })
                const client = new FetchHttpClient(config.serverDomain);
                client.addMiddleware(json());
                client.get('/database/storedprocedures/'+data.selectServer+'/'+data.selectDatabase+'/'+data.spName,
                            {headers:{'User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            sql: response.jsonData.data.procedureData
                        })
                    })
                
            }
            else if('view' === data.type){
                this.setState({
                    selectServer: data.selectServer,
                    selectDatabase: data.selectDatabase,
                })
                const client = new FetchHttpClient(config.serverDomain);
                client.addMiddleware(json());
                client.get('/database/views/'+data.selectServer+'/'+data.selectDatabase+'/'+data.viewName,
                            {headers:{'User-Token': this.state.token}})
                    .then(response => {
                        this.setState({
                            sql: response.jsonData.data.viewData
                        })
                    })
            }
            else if('database' === data.type){
                const client = new FetchHttpClient(config.serverDomain);
                client.addMiddleware(json());
                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
                    this.setState({
                        selectServer: data.selectServer,
                        selectDatabase: data.selectDatabase,
                        selectServerName: response.jsonData.data.dbServerName,
                        selectServerType: response.jsonData.data.dbServerType,
                        historySql: localStorage.getItem(data.selectServer+'_history_sql')===null?[]:JSON.parse(localStorage.getItem(data.selectServer+'_history_sql'))
                    })
                })
            }
            else if('server' === data.type){
                const client = new FetchHttpClient(config.serverDomain);
                client.addMiddleware(json());
                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
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
        let sql = ''===this.state.selectedSql?this.state.sql:this.state.selectedSql;
        if('' === sql){
            confirm({
                title:'提示',
                content: '请输入SQL语句后再执行',
                onOk(){                        },
                onCancel(){                        }
            });
            return;
        }
        if('' === this.state.selectDatabase){
            confirm({
                title:'提示',
                content: '请选择数据库后再执行',
                onOk(){                        },
                onCancel(){                        }
            });
            return;
        }
        this.setState({
            queryLoading: true,
            queryResult: []
        });
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/database/query/'+this.state.selectServer+'/'+this.state.selectDatabase,
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
                this.setState({
                    queryResult: response.jsonData.data,
                    dataAreaRefresh: [sql]
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

    printTableHeader() {
        if(this.state.queryResult[0] !== undefined){
            let data = this.state.queryResult[0]
            return (
                <tr>
                {
                Object.keys(data).map(key => {
                    return(<th>{key}</th>)
                })
                }
                </tr>
            );
        }
        else{
            return(
                <tr><th></th></tr>
            )
        }
    }

    static dataColumnShow(columnData) {
        if(null == columnData){
            return 'null'
        }
        switch(typeof columnData) {
            case 'boolean':
                return columnData?'true':'false';
            default:
                return columnData;
        }
    }

    handleTextareaChange(e) {
        this.setState({
            sql: e
        })
    }
    printTableData() {
        
        if(this.state.queryResult[0] !== undefined){
            let data = this.state.queryResult

            return (
                data.map( row => {
                return(<tr key={row[0]}>{
                    Object.keys(row).map( col => {
                        return (<td>{PageContent.dataColumnShow(row[col])}</td>)
                    })
                    }</tr>)
                }
                )

            );
        }
        else{
            return(
                <tr><td>无数据...</td></tr>
            )
        }
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

        console.log(beforeSql)
        console.log(rearSql)
        this.setState({
            beforeSql: beforeSql,
            rearSql: rearSql
        })
    }
    mouseSelected(dom) {
        this.saveCursorValue(dom)
        let sqlCursor = dom.getCursor()

        let sql = dom.getValue()
        console.log(sql)
        this.setState({
            selectedSql: dom.getSelection()
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
    
    render(){
        const {sql, queryResult, selectDatabase} = this.state;

        return (
            <div className="right_area">
                <div id="menubar">
                    <div id="serverinfo">
                        <img src={dot}  alt="SERVERIMG" className="icon ic_s_host "/>
                        服务器: {this.state.selectServerName} ({this.state.selectServerType}) 
                        <span className={'' === selectDatabase?'hide':'none'}>
                        &gt;&gt; <img src={dot} className="icon ic_s_db " alt="DBIMG"/>数据库: {this.state.selectDatabase}
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
                                    <CodeMirror ref="editor" onCursorActivity={this.mouseSelected.bind(this)} value={sql} onBeforeChange={(editor, data, value) => { this.setState({sql: value});}}  options={options} />
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
                        <input className="btn btn-primary" type="submit" id="button_submit_query" name="SQL"
                               tabIndex="200" value="执行SQL" onClick={this.execeteSql.bind(this)} />
                               { 0 !== queryResult.length? (<CSVLink data={queryResult}>导出查询结果</CSVLink>):(<span></span>) }
                               
                            <div className="clearfloat"></div>
                    </fieldset>
                    <div className={this.state.queryLoading || this.state.queryResult.length === 0?'hide':'responsivetable'}>
                        <Spreadsheet data={queryResult} dataAreaRefresh={this.state.dataAreaRefresh}></Spreadsheet>
                        {/*<table className="table_results ajax pma_table">*/}
                        {/*    <thead>*/}
                        {/*        {this.printTableHeader()}*/}
                        {/*        */}
                        {/*    </thead>*/}
                        {/*    <tbody>*/}
                        {/*            {this.printTableData()}*/}
                        {/*    </tbody>*/}
                        {/*</table>*/}
                    </div>
                    <div className={this.state.queryLoading?'query_load':'hide'}>
                        <Spin indicator={antIcon} />数据查询中...
                    </div>
                    <div className={!this.state.queryLoading && 0 === this.state.queryResult.length ?'query_load':'hide'}> 
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    </div>
                </div>
                
            </div>

            
        )
    }
}

export default PageContent