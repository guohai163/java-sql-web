import React from 'react';
import FetchHttpClient, { json } from 'fetch-http-client';
import './Navigation.css';
import logo from './images/logo.svg'
import dot from './images/dot.gif'
import config from './config'
import Pubsub from 'pubsub-js'
import cookie from 'react-cookies'
import { LoadingOutlined } from '@ant-design/icons';
import { Modal, Spin, Input, Form, Select, Tag } from 'antd';
import cache from './utils';
import * as webauthnJson from "@github/webauthn-json";

const { confirm } = Modal;
const { Option, OptGroup } = Select;
const antIcon = <LoadingOutlined style={{ fontSize: 24 }} spin />;
const CACHE_TTL = 1000*60*60*24;
let table_result ;

class Navigation extends React.Component {
    constructor(props){
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
            indexData: [],
            token: cookie.load('token'),
            tableLoading: false,
            filterTableList: [],
            filterSpList: [],
            passVisible: false,
            inputData: {},
            dbGroup: [],
            viewList: []
            
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
            deskHeight:window.innerHeight - 158
        })
    }
    getServerList() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/database/serverlist',{headers:{'User-Token': this.state.token}})
            .then(response => {
                console.log(response)
                if(response.jsonData.status) {
                    this.setState({
                        serverList: response.jsonData.data
                    })
                }
            })
            .catch(rejected => {
                console.log('catch',rejected)

            })
        client.get('/database/server/group',{headers:{'User-Token': this.state.token}})
            .then(response => {
                console.log(response.jsonData)
                if(response.jsonData.status) {
                    this.setState({
                        dbGroup: response.jsonData.data
                    })
                }
            })
            .catch(rejected => {
                console.log('catch',rejected)

            })
    }
    dbChange(dbName,event) {
        let start_time = Date.now();
        console.log('start_time',start_time)
        if(dbName === this.state.selectDatabase) {
            this.setState({
                selectDatabase: ''
            })
            return
        }
        this.setState({
            selectDatabase: dbName,
            tableLoading: true,
            tableList: [],
            spList: [],
            viewList: []
        })
        const selectData = {
            selectServer: this.state.selectServer,
            selectDatabase: dbName,
            type: 'database'
            };
        Pubsub.publish('dataSelect', selectData);

        // 获取表
        // 优先尝试从缓存获取
        const requestKey = '/database/tablelist/'+this.state.selectServer+'/'+dbName;
    
        const tableData = cache.get(requestKey);
        console.log('加载完缓存：',Date.now()-start_time);
        if(null === tableData) {
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get(requestKey,{headers:{'User-Token': this.state.token}}).then(response => {
                if(response.jsonData.status) {

                    this.setState({
                        tableList: response.jsonData.data,
                        filterTableList: response.jsonData.data,
                        tableLoading: false
                    })
                    cache.set(requestKey, response.jsonData.data, CACHE_TTL);
    
                }
                else{
                    this.setState({tableList: [], filterTableList: [], tableLoading: false})
                }
            })
        }
        else {
            this.setState({
                tableList: tableData,
                filterTableList: tableData,
                tableLoading: false
            })
        }
        console.log('方法走完存：',Date.now()-start_time);
        //获取存储过程
        // this.getSpList(dbName);
    }
    serverChange(value) {

            let url = '/database/dblist/'+value

            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get(url,{headers:{'User-Token': this.state.token}}).then(response => {
                console.log(response.jsonData)
                if(response.jsonData.status){
                    this.setState({
                        selectServer: value,
                        dbList: response.jsonData.data
                    })
                    const selectData = {
                        selectServer: this.state.selectServer,
                        type: 'server'
                        };
                    Pubsub.publish('dataSelect', selectData);
                }
                
            })
    }
    filterTable(parm) {
        console.log(parm.target.value)

        let filterResult = this.state.tableList.filter(item => item.tableName.indexOf(parm.target.value) !== -1)
        let filterSpResult = this.state.spList.filter(item => item.procedureName.indexOf(parm.target.value) !== -1)

        this.setState({
            filterTableList: filterResult,
            filterSpList: filterSpResult
        })
        
    }
    getViewsList(dbName){
        const requestKey = '/database/views/'+this.state.selectServer+'/'+dbName
        const viewsData = cache.get(requestKey)
        if(null === viewsData) {
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get(requestKey,{headers:{'User-Token': this.state.token}}).then(response => {
                console.log(response)
                if(response.jsonData.status) {
                    if(0 === response.jsonData.data.length){
                        confirm({
                            title:'提示',
                            content: '该库无视图',
                            onOk(){
                            },
                            onCancel(){
                            }
                        });
                    }
                    this.setState({
                        viewList: response.jsonData.data
                    })
                    cache.set(requestKey, response.jsonData.data, CACHE_TTL)
                }
                else{
                    this.setState({viewList:[]})
                }
            })
        }
        else{
            if(0 === viewsData.length){
                confirm({
                    title:'提示',
                    content: '该库无视图',
                    onOk(){
                    },
                    onCancel(){
                    }
                });
            }
            this.setState({
                viewList: viewsData
            })
        }
    }
    getSpList(dbName) {
        const requestKey = '/database/storedprocedures/'+this.state.selectServer+'/'+dbName
        const spData = cache.get(requestKey)
        if(null === spData) {
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get(requestKey,{headers:{'User-Token': this.state.token}}).then(response => {
    
                if(response.jsonData.status) {
                    if(0 === response.jsonData.data.length){
                        confirm({
                            title:'提示',
                            content: '该库无存储过程',
                            onOk(){
                            },
                            onCancel(){
                            }
                        });
                    }
                    this.setState({
                        spList: response.jsonData.data,
                        filterSpList: response.jsonData.data
                    })
                    cache.set(requestKey, response.jsonData.data, CACHE_TTL)
                }
                else{
                    this.setState({spList:[], filterSpList:[]})
                }
            })
        }
        else{
            if(0 === spData.length){
                confirm({
                    title:'提示',
                    content: '该库无存储过程',
                    onOk(){
                    },
                    onCancel(){
                    }
                });
            }
            this.setState({
                spList: spData,
                filterSpList: spData
            })
        }

    }
    viewChange(viewName, event){
        const selectData = {
            selectServer: this.state.selectServer,
            selectDatabase: this.state.selectDatabase,
            viewName: viewName,
            type: 'view'
        }
        Pubsub.publish('dataSelect', selectData);
    }

    spChange(spName,event) {
        const selectData = {selectServer: this.state.selectServer,
            selectDatabase: this.state.selectDatabase,
            spName: spName,
            type: 'sp'
        };
        Pubsub.publish('dataSelect', selectData);
    }
    sendColumnName(columnName, event){
        const selectData = {selectServer: this.state.selectServer,
            selectDatabase: this.state.selectDatabase,
            selectColumn: columnName,
            type: 'column'
        };
        Pubsub.publish('dataSelect', selectData);
    }


    sendTableName(tableName,event) {
        table_result = false;
        window.setTimeout(check, 300);
        var that = this;
        function check() {
            if (table_result !== false) return;
            console.log('单击')
            const selectData = {selectServer: that.state.selectServer,
                selectDatabase: that.state.selectDatabase,
                selectTable: tableName,
                type: 'tableName'
            };
            Pubsub.publish('dataSelect', selectData);
        }


    }
    tableChange(tableName,event){
        console.log('双击')
        table_result = true;
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
        console.log(tableName)
        if(this.state.showTableColumn !== tableName){
            this.setState({showTableColumn:tableName})
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get('/database/columnslist/'+this.state.selectServer+'/'+this.state.selectDatabase+'/'+tableName,{headers:{'User-Token': this.state.token}})
                .then(response => {
                    if(response.jsonData.status){
                        this.setState({
                            columntData: response.jsonData.data
                        })
                    }
                })
            client.get('/database/indexeslist/'+this.state.selectServer+'/'+this.state.selectDatabase+'/'+tableName,{headers:{'User-Token': this.state.token}})
                .then(response => {
                    if(response.jsonData.status){
                        console.log(response.jsonData.data)
                        this.setState({
                            indexData: response.jsonData.data
                        })
                    }
                })
        }
        else{
            this.setState({showTableColumn: ''});
        }

    }
    logout(){
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/user/logout',{headers:{'User-Token': this.state.token}})
            .then(response => {
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
    jumpAdmin(){
        if('admin' === config.userName){
            window.location.href = '/admin'
        }
        else{
            confirm({
                title:'提示',
                content: '您无权限进入管理页面',
                onOk(){
                },
                onCancel(){
                }
            });
        }
    }
    modalHandleOk(){
        // 修改密码弹层确认
        console.log(this.state.inputData)
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/api/backstage/change_new_pass',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
            body:this.state.inputData['userNewPassword']})
         .then(response => {
            console.log(response.jsonData)
            if(true === response.jsonData.status){
                confirm({
                    title:'提示',
                    content: '密码修改成功',
                    onOk(){},
                    onCancel(){}
                });
                this.setState({passVisible:false});
            }
            else{
                confirm({
                    title:'提示',
                    content: response.jsonData.data,
                    onOk(){                        },
                    onCancel(){                        }
                });
            }
         })
    }
    passKeyBind(){

        // 判断浏览器是否支持passkey
        if(!webauthnJson.supported()){
            confirm({
                title:'提示',
                content: "当前系统环境无法开启passKey功能",
                onOk(){                        },
                onCancel(){                        }
            });
            return;
        }

        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/webauthn/create',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token }})
        .then(async (response)=> {
            if(true === response.jsonData.status){
                const publicKeyCredential = await webauthnJson.create(JSON.parse(response.jsonData.data));
                console.log(JSON.stringify({publicKeyCredentialJson: publicKeyCredential}))
                client.post('/webauthn/register',{headers: { 'Content-Type': 'application/json','User-Token': this.state.token },
                    body:JSON.stringify(publicKeyCredential)}
                )
                .then(response =>{
                    console.log(response.jsonData)
                    if(true === response.jsonData.status){
                        confirm({
                            title:'提示',
                            content: 'passKey绑定成功',
                            onOk(){},
                            onCancel(){}
                        });
                        this.setState({passVisible:false});
                    }
                    else{
                        confirm({
                            title:'提示',
                            content: response.jsonData.data,
                            onOk(){                        },
                            onCancel(){                        }
                        });
                    }
                })
            }
            else{
                confirm({
                    title:'提示',
                    content: response.jsonData.data,
                    onOk(){                        },
                    onCancel(){                        }
                });
            }
        })

    }
    modalHandleCancel(){
        this.setState({passVisible:false,inputData:{}});
    }
    showPassModal(){
        this.setState({passVisible:true});
    }
    onInputChange(e){
        let data = this.state.inputData;
        data[e.target.id] = e.target.value
        this.setState({
            inputData: data
        })
    }
    render(){
        const {deskHeight, columntData, spList, passVisible, viewList, indexData} = this.state;
        return (
            <div id='navigation'>
                <div id='navigation_resizer'></div>
                <div id="navigation_content">
                    <div id="navigation_header">
                        <div id="logo">
                            <img src={logo} alt="logo" />{config.version}
                        </div>
                        <div id="navipanellinks">

                            <a title="常用SQL" href="/guid" target="_blank">
                                <img src={dot} alt="常用SQL" className="icon ic_s_sqlguid"></img>
                            </a>
                            <a title="刷新" onClick={this.getServerList.bind(this)}>
                                <img src={dot} alt="刷新" className="icon ic_s_reload"></img>
                            </a>
                            <a title="修改密码" onClick={this.showPassModal.bind(this)}>
                                <img src={dot} alt="修改密码" className="icon ic_u_pass"></img>
                            </a>
                            <a title='passkey' onClick={this.passKeyBind.bind(this)}>
                                <img src={dot} alt="passkey" className='icon ic_w_authn'></img>
                            </a>
                            <a href="#" title="设置" onClick={this.jumpAdmin.bind(this)}>
                                <img src={dot} alt="setting" className={'admin' === config.userName?'icon ic_s_cog':'hide'}></img>
                            </a>
                            <a href="#" title="退出" onClick={this.logout.bind(this)}>
                                <img src={dot} alt="exit" className="icon ic_s_loggoff"></img>
                            </a>
                            
                        </div>
                    </div>
                    <div id="navigation_tree">
                        <div className="navigation_server">
                            <label>服务器：</label>
                            <Select placeholder="请选择服务器" style={{ width: 200 }} onChange={this.serverChange}>
                                {this.state.dbGroup.map(group => 
                                    <OptGroup label={group}>
                                        {this.state.serverList.filter(item => item.dbGroup === group).map(server =>
                                            <Option value={server.code}>{server.dbServerName}</Option>
                                            )}
                                    </OptGroup>
                                    )}
          
                            </Select>
                        </div>
                        <div id="navigation_tree_content" style={{height: deskHeight}}>
                            <ul>
                                {this.state.dbList.map(db => 
                                    <li className="database" key={db.dbName}>
                                        <div className="block">
                                            <i></i><b></b>
                                            <a className="expander loaded" href="#" onClick={this.dbChange.bind(this,db.dbName)}><span className="hide aPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                                            <span className="hide vPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                                            <span className="hide pos">0</span>
                                            <img src={dot} title="扩展/收起" alt="扩展/收起" className={this.state.selectDatabase === db.dbName?'icon ic_b_minus':'icon ic_b_plus'}></img>
                                            </a>
                                        </div>
                                        <div className="block">
                                            <a href="#" onClick={this.dbChange.bind(this,db.dbName)}>
                                                <img src={dot} alt="数据库操作" className="icon ic_s_db"></img>
                                            </a>
                                        </div>
                                        <a className="hover_show_full" onClick={this.dbChange.bind(this,db.dbName)}>{db.dbName}</a>
                                        <div className={this.state.tableLoading && this.state.selectDatabase === db.dbName?'clearfloat':'hide'}><Spin indicator={antIcon} /></div>
                                        <div className={this.state.selectDatabase === db.dbName?'list_container':'hide'}>                                            
                                            <ul>
                                                <li className="filter_input"><Input placeholder="Filter" size="small" allowClear onChange={this.filterTable.bind(this)}></Input></li>
                                                {this.state.selectDatabase !== db.dbName?'':this.state.filterTableList.map(table =>
                                                    <li className="view" key={table.tableName}>
                                                    <div className="block"><i></i>
                                                    <a className="expander" href="#">
                                                    <span className="hide pos2_name">views</span><span className="hide pos2_value">0</span>
                                                    <img src={dot} title="扩展/收起" alt="扩展/收起" className={this.state.showTableColumn === table.tableName?'icon ic_b_minus':'icon ic_b_plus'} onClick={this.showTableColumn.bind(this,table.tableName)}></img>
                                                    </a></div>
                                                    <div className="block"><a href="#"><img src={dot} title="视图" alt="视图" className="icon ic_b_props" /></a></div>
                                                    <a className="hover_show_full" href="#" title="" onDoubleClick={this.tableChange.bind(this,table.tableName)} onClick={this.sendTableName.bind(this,table.tableName)}> {table.tableName} ({table.tableRows})</a>
                                                    <div className="clearfloat"></div>
                                                    <div className={this.state.showTableColumn === table.tableName?'list_container':'hide'}>
                                                        <ul>
                                                            {this.state.showTableColumn !== table.tableName?'':columntData.map(column =>
                                                                <li onClick={this.sendColumnName.bind(this,column.columnName)}>{column.columnName}({column.columnType}{""===column.columnLength?"":"("+column.columnLength+")"},{column.columnIsNull})<br /> - <Tag color="green">{""===column.columnComment?"NULL":column.columnComment}</Tag></li>
                                                            )}
                                                            {this.state.showTableColumn != table.tableName ? '' : indexData.map(indexD =>
                                                                <li><img src={dot} title="视图" alt="视图" className="icon ic_b_views" />{indexD.indexName}[{indexD.indexKeys}]</li>
                                                            )}
                                                        </ul>
                                                    </div>
                                                    </li>
                                                )}
                                                <li className="view" key="procedure">
                                                    <a href="#" onClick={this.getSpList.bind(this,db.dbName)}>
                                                    <img src={dot} className={spList.length === 0?'icon ic_b_plus':'icon ic_b_minus'}></img>
                                                    <img src={dot} title="存储过程" alt="存储过程" className="icon ic_b_routines" />
                                                    存储过程</a>
                                                    <div className={spList.length === 0?'hide':'list_container'}>
                                                        <ul>
                                                        {spList.map( sp =>
                                                                <li className="view">
                                                                <div className="block"><a href="#"><img src={dot} title="视图" alt="视图" className="icon ic_b_routines" /></a></div>
                                                                <a className="hover_show_full" href="#" title="" onClick={this.spChange.bind(this,sp.procedureName)}> {sp.procedureName}</a>
                                                                <div className="clearfloat"></div>
                                                                </li>
                                                        )}
                                                        </ul>
                                                    </div>
                                                </li>
                                                <li className="view" key="views">
                                                    <a href="#" onClick={this.getViewsList.bind(this,db.dbName)}>
                                                    <img src={dot} className={spList.length === 0?'icon ic_b_plus':'icon ic_b_minus'}></img>
                                                    <img src={dot} title="视图" alt="视图" className="icon ic_b_views" />
                                                    视图</a>
                                                    <div className={viewList.length === 0?'hide':'list_container'}>
                                                        <ul>
                                                        {viewList.map( sp =>
                                                                <li className="view">
                                                                <div className="block"><a href="#"><img src={dot} title="视图" alt="视图" className="icon ic_b_views" /></a></div>
                                                                <a className="hover_show_full" href="#" title="" onClick={this.viewChange.bind(this,sp.viewName)}> {sp.viewName}</a>
                                                                <div className="clearfloat"></div>
                                                                </li>
                                                        )}
                                                        </ul>
                                                    </div>
                                                </li>
                                            </ul>
                                        </div>
                                    </li>
                                )}
                                
                            </ul>
                        </div>
                    </div>
                </div>
                <Modal
                        title="修改密码"
                        visible={passVisible}
                        onOk={this.modalHandleOk.bind(this)}
                        onCancel={this.modalHandleCancel.bind(this)}
                    >
                    <Form size="small" labelCol={{ span: 7 }}>
                    <Form.Item label="请输入新密码" rules={[{ required: true, message: '请输入密码!' }]}>
                    <Input.Password onChange={this.onInputChange.bind(this)} value={this.state.inputData['userNewPassword']} id="userNewPassword"/>
                    </Form.Item>
                    </Form>
                    
                </Modal>
            </div>
            
        )
    }
}

export default Navigation