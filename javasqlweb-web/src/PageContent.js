import React from 'react';
import Pubsub from 'pubsub-js'
import dot from './images/dot.gif'
import './PageContent.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import { CSVLink } from "react-csv";

import cookie from 'react-cookies'

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
            token: cookie.load('token')
        }
    }

    componentDidMount() {
        console.log('PageContent', this.props.token)
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
                            console.log(response.jsonData)
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
                        console.log(response.jsonData)
                        this.setState({
                            sql: response.jsonData.data.procedureData
                        })
                    })
                
            }

        })
    }



    execeteSql() {
        console.log('executeSql', this.state.token)
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/database/query/'+this.state.selectServer+'/'+this.state.selectDatabase,
        {headers:{'Content-Type': 'text/plain','User-Token': this.state.token},body: this.state.sql}).then(response => {
            if(response.jsonData.status){
                console.log(response.jsonData.data)
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
            sql: e.target.value
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
    render(){
        const {sql, queryResult} = this.state;

        return (
            <div>
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
                                    <textarea value={sql} onChange={this.handleTextareaChange.bind(this)} tabIndex="100" name="sql_query" id="sqlquery" cols="40" rows="20">

                                    </textarea>
                                </div>
                                <div id="tablefieldscontainer"><label>字段</label>
                                    <select id="tablefields" name="dummy" size="13" multiple="multiple"  >
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
                    <div className="responsivetable">
                        <table className="table_results ajax pma_table">
                            <thead>
                                {this.printTableHeader()}
                                
                            </thead>
                            <tbody>
                                    {this.printTableData()}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            
        )
    }
}

export default PageContent