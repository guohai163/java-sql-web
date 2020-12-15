import React from 'react';
import Pubsub from 'pubsub-js'
import dot from './images/dot.gif'
import './PageContent.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import { CSVLink } from "react-csv";
import { Modal, Spin, Empty } from 'antd';
import cookie from 'react-cookies';
import { LoadingOutlined } from '@ant-design/icons';

import {Controlled as CodeMirror} from 'react-codemirror2';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/sql/sql';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/hint/sql-hint.js';
import 'codemirror/theme/lucario.css';

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 34 }} spin />;

const options={
    lineNumbers: true,                     //显示行号
    mode: {name: "text/x-mysql"},          //定义mode
    extraKeys: {"Ctrl": "autocomplete"},   //自动提示配置
    theme: "lucario"                  //选中的theme
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
            sqlValue: ''
        }
    }

    componentDidMount() {
        
        Pubsub.subscribe('dataSelect', (msg, data) => {
            if('table' === data.type){
                const client = new FetchHttpClient(config.serverDomain);
                client.addMiddleware(json());
                client.get('/database/serverinfo/'+data.selectServer,{headers:{'User-Token': this.state.token}}).then( response => {
                    let sql = 'mssql' === response.jsonData.data.dbServerType ? 'SELECT top 100 * FROM ' + data.selectTable : 'SELECT * FROM '+data.selectDatabase+'.'+data.selectTable + ' limit 100'

                    this.setState({
                        selectServer: data.selectServer,
                        selectDatabase: data.selectDatabase,
                        selectTable: data.selectTable,
                        selectServerName: response.jsonData.data.dbServerName,
                        selectServerType: response.jsonData.data.dbServerType,
                        sql: sql
                    })
                    client.get('/database/columnslist/'+data.selectServer+'/'+data.selectDatabase+'/'+data.selectTable,
                                {headers:{'User-Token': this.state.token}}).
                        then(response => {
                            if(response.jsonData.status) {
                                this.setState({
                                    tableColumns: response.jsonData.data
        
                                })
                            }
                        })
                })

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
                            {headers:{'User-Token': this.state.token}}).
                    then(response => {
                        this.setState({
                            sql: response.jsonData.data.procedureData
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

                    })
                })
            }

        })
    }



    execeteSql() {
        
        this.setState({
            queryLoading: true,
            queryResult: []
        });
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/database/query/'+this.state.selectServer+'/'+this.state.selectDatabase,
        {headers:{'Content-Type': 'text/plain','User-Token': this.state.token},body: this.state.sql}).then(response => {
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
                this.setState({
                    queryResult: response.jsonData.data
                })
            }else {
                this.setState({
                    queryResult: []
                })
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
                return(<tr>{
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
    render(){
        const {sql, queryResult} = this.state;

        return (
            <div className="right_area">
                <div id="menubar">
                    <div id="serverinfo">
                        <img src={dot} title="" alt="" className="icon ic_s_host item"/>
                        服务器: {this.state.selectServerName} ({this.state.selectServerType}) >> 
                        <img src={dot} title="" alt="" className="icon ic_s_db item"/>
                        数据库: {this.state.selectDatabase}
                    </div>
                </div>
                <div className='page_content'>
                    <div id="queryboxContainer">
                        <fieldset id="queryboxf">
                            <div id="queryfieldscontainer">
                                <div id="sqlquerycontainer">
                                   
                                    {/* <textarea value={sql} onChange={this.handleTextareaChange.bind(this)} tabIndex="100" name="sql_query" id="sqlquery" cols="40" rows="20">

                                    </textarea> */}
                                    <CodeMirror ref="editor" value={sql} onBeforeChange={(editor, data, value) => { this.setState({sql: value});}}  options={options} />
                                    * 敲入关键字首字母后可以使用Ctrl进行快速补全
                                </div>
                                <div id="tablefieldscontainer"><label>字段</label>
                                    <select id="tablefields" name="dummy" size="13" multiple="multiple" onChange={this.selectColumn.bind(this)}>
                                        {this.state.tableColumns.map( column =>
                                            <option value={column.columnName} title="">{column.columnName} - {column.columnType}({column.columnLength})</option>
                                        )}
                                    </select>
                                </div>
                                <div className="clearfloat"></div>
                            </div>
                        </fieldset>
                    </div>
                    <fieldset id="queryboxfooter" className="tblFooters">
                                       
                        <input className="btn btn-primary" type="submit" id="button_submit_query" name="SQL"
                               tabIndex="200" value="执行" onClick={this.execeteSql.bind(this)} />
                               { 0 !== queryResult.length? (<CSVLink data={queryResult}>导出查询结果</CSVLink>):(<span></span>) }
                               
                            <div className="clearfloat"></div>
                    </fieldset>
                    <div className={this.state.queryLoading || this.state.queryResult.length === 0?'hide':'responsivetable'}>
                        <table className="table_results ajax pma_table">
                            <thead>
                                {this.printTableHeader()}
                                
                            </thead>
                            <tbody>
                                    {this.printTableData()}
                            </tbody>
                        </table>
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