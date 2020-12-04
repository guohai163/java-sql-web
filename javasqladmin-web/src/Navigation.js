import React from 'react';
import FetchHttpClient, { json } from 'fetch-http-client';
import './Navigation.css';
import logo from './images/logo_left.png'
import dot from './images/dot.gif'
import config from './config'

class Navigation extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            serverList: [],
            selectServer: '0',
            dbList: [],
            selectDatabase: ''
        }
        this.serverChange = this.serverChange.bind(this);
    }
    componentDidMount() {
        this.getServerList()
    }
    getServerList() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/database/serverlist').then(response => {
            console.log(response.jsonData);
            if(response.jsonData.status) {
                this.setState({
                    serverList: response.jsonData.data
                })
            }
        })
    }
    dbChange(dbName,event) {
        console.log(dbName)
        this.setState({
            selectDatabase: dbName
        })
        // 获取表

        //获取存储过程
    }
    serverChange(event) {
        console.log(event)
        //当不为“请选择服务器”时进行相应 操作
        if('0' !== event.target.value) {
            console.log('需要进行操作');
            const client = new FetchHttpClient(config.serverDomain);
            client.addMiddleware(json());
            client.get('/database/dblist/'+event.target.value).then(response => {
                console.log(response.jsonData.data);
                if(response.jsonData.status){
                    this.setState({
                        selectServer: event.target.value,
                        dbList: response.jsonData.data
                    })
                }
                
            })

        }
    }
    render(){
        return (
            <div id='navigation'>
                <div id='navigation_resizer'></div>
                <div id="navigation_content">
                    <div id="navigation_header">
                        <div id="logo">
                            <img src={logo} alt="logo" />
                        </div>
                        <div id="navipanellinks">
                            <a href="#" title="设置">
                                <img src={dot} alt="setting" className="icon ic_s_cog"></img>
                            </a>
                            <a href="#" title="退出">
                                <img src={dot} alt="exit" className="icon ic_s_loggoff"></img>
                            </a>
                        </div>
                    </div>
                    <div id="navigation_tree">
                        <div className="navigation_server">
                            <label>服务器：</label>
                            <select id="select_server" onChange={this.serverChange}>
                                <option value="0">请选择服务器</option>
                                {this.state.serverList.map(server => <option value={server.code}>{server.dbServerName}</option>)}
                            </select>
                        </div>
                        <div id="navigation_tree_content">
                            <ul>
                                {this.state.dbList.map(db => 
                                    <li className="database">
                                        <div className="block">
                                            <i></i><b></b>
                                            <a className="expander loaded" href="#"><span className="hide aPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                                            <span className="hide vPath">cm9vdA==.aW5mb3JtYXRpb25fc2NoZW1h</span>
                                            <span className="hide pos">0</span>
                                            <img src={dot} title="扩展/收起" alt="扩展/收起" className="icon ic_b_plus"></img>
                                            </a>
                                        </div>
                                        <div className="block">
                                            <a href="#">
                                                <img src={dot} alt="数据库操作" className="icon ic_s_db"></img>
                                            </a>
                                        </div>
                                        <a className="hover_show_full" onClick={this.dbChange.bind(this,db.dbName)}>{db.dbName}</a>
                                        <div class="clearfloat"></div>
                                        <div className={this.state.selectDatabase == db.dbName?'list_container':'hide'}>                                            
                                            <ul>
                                                <li className="view">
                                                <div class="block"><i></i><span class="hide pos2_name">views</span><span class="hide pos2_value">0</span></div>
                                                <div className="block"><a href="#"><img src={dot} title="视图" alt="视图" className="icon ic_b_props" /></a></div>
                                                <a class="hover_show_full" href="#" title="">ALL_PLUGINS</a>
                                                </li>
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