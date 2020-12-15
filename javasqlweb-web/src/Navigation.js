import React from 'react';
import FetchHttpClient, { json } from 'fetch-http-client';
import './Navigation.css';
import logo from './images/logo_left.png'
import dot from './images/dot.gif'
import config from './config'
import Pubsub from 'pubsub-js'
import cookie from 'react-cookies'
import { LoadingOutlined } from '@ant-design/icons';
import { Modal, Spin } from 'antd';

const { confirm } = Modal;
const antIcon = <LoadingOutlined style={{ fontSize: 24 }} spin />;

class Navigation extends React.Component {
    constructor(props){
        console.log(window.innerHeight)
        super(props)
        this.state = {
            serverList: [],
            selectServer: '0',
            dbList: [],
            selectDatabase: '',
            tableList: [],
            spList: [],
            selectTable: '',
            deskHeight: 0,
            showTableColumn: '',
            columntData: [],
            spList: [],
            token: cookie.load('token'),
            tableLoading: false
        }
        this.serverChange = this.serverChange.bind(this);
        this.handleSize = this.handleSize.bind(this)
    }
    componentDidMount() {
        this.getServerList()
        this.handleSize()
        window.addEventListener('resize', this.handleSize);
    }
    componentWillUnmount() {
        window.removeEventListener('resize', this.handleSize);
    }
    handleSize = () => {
        this.setState({
            deskHeight:window.innerHeight - 120 
        })
    }
    getServerList() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/database/serverlist',{headers:{'User-Token': this.state.token}})
        .then(response => {
            if(response.jsonData.status) {
                this.setState({
                    serverList: response.jsonData.data
                })
            }
        })
        .catch(rejected => {
            console.log('catch',rejected)

        })
    }
    dbChange(dbName,event) {

        if(dbName === this.state.selectDatabase) {
            this.setState({
                selectDatabase: ''
            })
            return
        }
        this.setState({
            selectDatabase: dbName,
            tableLoading: true
        })
        // 获取表
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/database/tablelist/'+this.state.selectServer+'/'+dbName,{headers:{'User-Token': this.state.token}}).then(response => {
            if(response.jsonData.status) {
                this.setState({
                    tableList: response.jsonData.data,
                    tableLoading: false
                })
            }
            else{
                this.setState({tableList: [], tableLoading: false})
            }
        })
        //获取存储过程
        this.getSpList(dbName);
    }
    serverChange(event) {
        console.log(event)
        console.log(event.target.value)
        console.log(event.target.label)
        //当不为“请选择服务器”时进行相应 操作
        if('0' !== event.target.value) {
            console.log('需要进行操作');
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get('/database/dblist/'+event.target.value,{headers:{'User-Token': this.state.token}}).then(response => {
                console.log(response.jsonData)
                if(response.jsonData.status){
                    this.setState({
                        selectServer: event.target.value,
                        dbList: response.jsonData.data
                    })
                }
                
            })

        }
    }
    getSpList(dbName) {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/database/storedprocedures/'+this.state.selectServer+'/'+dbName,{headers:{'User-Token': this.state.token}}).then(response => {
            console.log(response.jsonData)
            if(response.jsonData.status) {
                this.setState({
                    spList: response.jsonData.data
                })
            }
            else{
                this.setState({spList:[]})
            }
        })
    }
    spChange(spName,event) {
        console.log(spName)
        const selectData = {selectServer: this.state.selectServer,
            selectDatabase: this.state.selectDatabase,
            spName: spName,
            type: 'sp'
        };
        Pubsub.publish('dataSelect', selectData);
    }
    tableChange(tableName,event){
        this.setState({
            selectTable: tableName
        })
        const selectData = {selectServer: this.state.selectServer,
                            selectDatabase: this.state.selectDatabase,
                            selectTable: tableName,
                            type: 'table'
                        };
        Pubsub.publish('dataSelect', selectData);
    }
    showTableColumn(tableName, event){
        this.setState({
            showTableColumn: tableName
        })
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/database/columnslist/'+this.state.selectServer+'/'+this.state.selectDatabase+'/'+tableName,{headers:{'User-Token': this.state.token}}).
            then(response => {
                    if(response.jsonData.status){
                        this.setState({
                            columntData: response.jsonData.data
                        })
                    }
            })
    }
    logout(){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/user/logout',{headers:{'User-Token': this.state.token}}).
            then(response => {
                if(response.jsonData.status){
                    this.setState({token:''})
                    cookie.remove('token', { path: '/' })
                    confirm({
                        title:'提示',
                        content: response.jsonData.message,
                        onOk(){
                            window.location.href = '/login'
                        },
                        onCancel(){
                            window.location.href = '/login'
                        }
                    });
                    
                }
            })
    }
    render(){
        const {deskHeight, columntData, spList} = this.state;
        return (
            <div id='navigation'>
                <div id='navigation_resizer'></div>
                <div id="navigation_content">
                    <div id="navigation_header">
                        <div id="logo">
                            <img src={logo} alt="logo" />
                        </div>
                        <div id="navipanellinks">
                            <button href="#" title="设置">
                                <img src={dot} alt="setting" className="icon ic_s_cog"></img>
                            </button>
                            <a href="#" title="退出" onClick={this.logout.bind(this)}>
                                <img src={dot} alt="exit" className="icon ic_s_loggoff"></img>
                            </a>
                        </div>
                    </div>
                    <div id="navigation_tree">
                        <div className="navigation_server">
                            <label>服务器：</label>
                            <select id="select_server" onChange={this.serverChange}>
                                <option value="0">请选择服务器</option>
                                {this.state.serverList.map(server => <option name={server.dbServerName} value={server.code}>{server.dbServerName}</option>)}
                            </select>
                        </div>
                        <div id="navigation_tree_content" style={{height: deskHeight}}>
                            <ul>
                                {this.state.dbList.map(db => 
                                    <li className="database">
                                        <div className="block">
                                            <i></i><b></b>
                                            <a className="expander loaded" href="#" onClick={this.dbChange.bind(this,db.dbName)}><span className="hide aPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                                            <span className="hide vPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                                            <span className="hide pos">0</span>
                                            <img src={dot} title="扩展/收起" alt="扩展/收起" className="icon ic_b_plus"></img>
                                            </a>
                                        </div>
                                        <div className="block">
                                            <a href="#" onClick={this.dbChange.bind(this,db.dbName)}>
                                                <img src={dot} alt="数据库操作" className="icon ic_s_db"></img>
                                            </a>
                                        </div>
                                        <a className="hover_show_full" onClick={this.dbChange.bind(this,db.dbName)}>{db.dbName}</a>
                                        <div className={this.state.tableLoading && this.state.selectDatabase === db.dbName?'clearfloat':'hide'}><Spin indicator={antIcon} /></div>
                                        <div className={this.state.selectDatabase == db.dbName?'list_container':'hide'}>                                            
                                            <ul>
                                                {this.state.tableList.map(table =>
                                                    <li className="view">
                                                    <div className="block"><i></i>
                                                    <a className="expander" href="#">
                                                    <span className="hide pos2_name">views</span><span className="hide pos2_value">0</span>
                                                    <img src={dot} title="扩展/收起" alt="扩展/收起" class="icon ic_b_plus" onClick={this.showTableColumn.bind(this,table.tableName)}></img>
                                                    </a></div>
                                                    <div className="block"><a href="#"><img src={dot} title="视图" alt="视图" className="icon ic_b_props" /></a></div>
                                                    <a className="hover_show_full" href="#" title="" onClick={this.tableChange.bind(this,table.tableName)}> {table.tableName} ({table.tableRows})</a>
                                                    <div class="clearfloat"></div>
                                                    <div className={this.state.showTableColumn == table.tableName?'list_container':'hide'}>
                                                        <ul>
                                                            {columntData.map(column =>
                                                                <li>{column.columnName}({column.columnType})</li>
                                                            )}
                                                        </ul>
                                                    </div>
                                                    </li>
                                                )}
                                               {spList.map( sp =>
                                                    <li className="view">
                                                    <div className="block"><i></i>
                                                    <a className="expander" href="#">
                                                    <span className="hide pos2_name">views</span><span className="hide pos2_value">0</span>
                                                    <img src={dot} title="扩展/收起" alt="扩展/收起" class="icon"></img>
                                                    </a></div>
                                                    <div className="block"><a href="#"><img src={dot} title="视图" alt="视图" className="icon ic_b_routines" /></a></div>
                                                    <a className="hover_show_full" href="#" title="" onClick={this.spChange.bind(this,sp.procedureName)}> {sp.procedureName}</a>
                                                    <div class="clearfloat"></div>
                                                    </li>
                                               )}
                                            </ul>
                                        </div>
                                    </li>
                                )}
                                
                            </ul>
                        </div>
                    </div>
                </div>
            </div>
            
        )
    }
}

export default Navigation