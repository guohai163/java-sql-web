import React from 'react';
import Pubsub from 'pubsub-js'
import dot from './images/dot.gif'
import './PageContent.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";

class TableHeader extends React.Component {
    constructor(props) {
        super(props)
        console.log(props.tableHeaderData)
    }
    render() {
        return(
            <tr>
            <th className="draggable">code</th>
            </tr>
        )
    }
}

class PageContent extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            selectServer: '0',
            selectDatabase: '',
            selectTable: '',
            tableColumns: [],
            sql: '',
            queryResult: []
        }
    }

    componentDidMount() {
        Pubsub.subscribe('dataSelect', (msg, data) => {
            this.setState({
                selectServer: data.selectServer,
                selectDatabase: data.selectDatabase,
                selectTable: data.selectTable,
                sql: 'SELECT * FROM ' + data.selectTable
            })
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get('/database/columnslist/'+data.selectServer+'/'+data.selectDatabase+'/'+data.selectTable).
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

    execeteSql() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/database/query/'+this.state.selectServer+'/'+this.state.selectDatabase,
        {headers:{'Content-Type': 'text/plain'},body: this.state.sql}).then(response => {
            if(response.jsonData.status){
                this.setState({
                    queryResult: response.jsonData.data
                })
            }

        })
    }

    printTableHeader() {
        if(this.state.queryResult[0] != undefined){
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

    printTableData() {
        if(this.state.queryResult[0] != undefined){
            let data = this.state.queryResult

            return (
                data.map( row => {
                return(<tr>{
                    Object.keys(row).map( col => {
                        return (<td>{typeof row[col] == 'boolean'?row[col] ?'true':'false':row[col] }</td>)
                    })
                    }</tr>)
                }
                
                    )

            );
        }
        else{
            return(
                <tr><td></td></tr>
            )
        }
    }
    render(){
        return (
            <div>
                <div id="menubar">
                    <div id="serverinfo">
                        <img src={dot} title="" alt="" className="icon ic_s_db item"/>
                        <a href="#" className="item">数据库: {this.state.selectDatabase}</a>
                    </div>
                </div>
                <div className='page_content'>
                    <div id="queryboxContainer">
                        <fieldset id="queryboxf">
                            <div id="queryfieldscontainer">
                                <div id="sqlquerycontainer">
                                    <textarea value={this.state.sql} tabIndex="100" name="sql_query" id="sqlquery" cols="40" rows="20">

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