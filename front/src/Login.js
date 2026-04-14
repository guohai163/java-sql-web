import React from 'react';
import {withRouter} from "react-router-dom";
import './Login.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import cookie from 'react-cookies'
import QRCode from 'qrcode.react'
import { Modal, Input, Tag } from 'antd';
import { UserOutlined,UnlockOutlined,VerifiedOutlined,AppleOutlined,AndroidOutlined } from '@ant-design/icons';
import * as webauthnJson from "@github/webauthn-json";
import queryString from "query-string";

const { confirm } = Modal;


class Login extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            userName: '',
            passWord: '',
            loginStep: 'LOGIN',
            authSecret: '',
            qrCode: '',
            otpPass: '',
            token: ''
        }
        this.handleInputChange = this.handleInputChange.bind(this)
    }
    componentDidMount() {


        const parsed = queryString.parse(this.props.location.search)

        if(undefined !== parsed.user_name &&  null !== parsed.user_name){
            this.autoCreateUser(parsed.user_name,parsed.timestamp,parsed.sign)
        }
    }
    handleInputChange(event){
        if('username' === event.target.name){
            this.setState({userName: event.target.value})
        }
        if('password' === event.target.name){
            this.setState({passWord: event.target.value})
        }
        if('otpPass' === event.target.name){
            this.setState({otpPass: event.target.value})
        }
    }
    autoCreateUser(userName, timestamp, sign){
        // 请求创建用户
        console.log(userName, timestamp, sign)
        let requestUrl = `/user/create_user/${userName}?timestamp=${timestamp}`
        console.log(requestUrl)
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post(requestUrl,{headers: { 'Content-Type': 'application/json', 'sign': sign },
            body:JSON.stringify({token: this.state.token, otpPass: this.state.otpPass})})
            .then(response => {
                console.log(response.jsonData)
                if(response.jsonData.status){
                    if('BINDING' === response.jsonData.data.authStatus){
                        this.setState({
                            authSecret: response.jsonData.data.authSecret,
                            loginStep: 'BIND',
                            qrCode: 'otpauth://totp/'+response.jsonData.data.userName+'@'+window.location.host+'?secret='+response.jsonData.data.authSecret+'&issuer=JavaSqlWeb',
                            token: response.jsonData.data.token
                        })
                    }
                    else if('BIND' === response.jsonData.data.authStatus){
                        this.setState({
                            loginStep: 'VERIFY',
                            token: response.jsonData.data.token
                        })
                    }
                }
                else{
                    confirm({
                        title:'提示',
                        content: `自动激活账号失败: ${response.jsonData.message}`,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
    }
    passkey(){
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
        const sessionKey = Math.random().toString(36).substring(2);
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.get('/webauthn/get',{headers: { 'Content-Type': 'application/json','Session-key':sessionKey }})
            .then(async (response)=> {
                    console.log(response.jsonData.data);
                    const publicKeyCredential = await webauthnJson.get(JSON.parse(response.jsonData.data));
                    console.log(publicKeyCredential)
                    client.post('/webauthn/signin',{headers: { 'Content-Type': 'application/json','Session-key':sessionKey },
                        body:JSON.stringify(publicKeyCredential)}
                    )
                    .then(response => {
                            console.log(response.jsonData.data);
                            if(response.jsonData.status){
                                cookie.save('token', response.jsonData.data.token, {path: '/'})
                                this.props.history.push('/');
                            }
                            else{
                                confirm({
                                    title:'提示',
                                    content: response.jsonData.message,
                                    onOk(){                        },
                                    onCancel(){                        }
                                });
                            }
                    }
                    )
                }
            )
    }
    login() {

        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/user/login',{headers: { 'Content-Type': 'application/json' },
            body:JSON.stringify({userName: this.state.userName, passWord: this.state.passWord})})
            .then(response => {
                if(200 !== response.status){
                    confirm({
                        title:'提示',
                        content: '服务器连接失败',
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
                else if(response.jsonData.status){

                    if('BINDING' === response.jsonData.data.authStatus){
                        this.setState({
                            authSecret: response.jsonData.data.authSecret,
                            loginStep: 'BIND',
                            qrCode: 'otpauth://totp/'+response.jsonData.data.userName+'@'+window.location.host+'?secret='+response.jsonData.data.authSecret+'&issuer=JavaSqlWeb',
                            token: response.jsonData.data.token
                        })
                    }
                    else if('BIND' === response.jsonData.data.authStatus){
                        this.setState({
                            loginStep: 'VERIFY',
                            token: response.jsonData.data.token
                        })
                    }
                }
                else{
                    confirm({
                        title:'提示',
                        content: response.jsonData.message,
                        onOk(){                        },
                        onCancel(){                        }
                    });
                }
            })
            .catch(rejected => {
                confirm({
                    title:'提示',
                    content: '服务器连接失败',
                    onOk(){                        },
                    onCancel(){                        }
                });
            })
    }
    bindOtp() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/user/bindotp',{headers: { 'Content-Type': 'application/json' },
            body:JSON.stringify({token: this.state.token, otpPass: this.state.otpPass})})
            .then(response => {
                if(response.jsonData.status){
                    cookie.save('token', this.state.token, {path: '/'})
                    this.props.history.push('/');
                }
            })
    }
    verifyOtp() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/user/verifyotp',{headers: { 'Content-Type': 'application/json' },
        body:JSON.stringify({token: this.state.token, otpPass: this.state.otpPass})})
        .then(response => {
            if(response.jsonData.status){
                cookie.save('token', this.state.token, {path: '/'})
                this.props.history.push('/');
            }
            else{
                confirm({
                    title:'提示',
                    content: response.jsonData.message,
                    onOk(){                        },
                    onCancel(){                        }
                });
            }
        })
    }
    render(){
        return (
            <div className="center">

                <div className={this.state.loginStep === 'LOGIN'?'container':'hide'}>
                    <fieldset>
                    <legend>登录</legend>
                    <div className="item">
                    <Input prefix={<UserOutlined />} placeholder="用户名" name="username" onChange={this.handleInputChange} />
                    </div>
                    <div className="item">
                    <Input.Password prefix={<UnlockOutlined />} placeholder="密码" name="password" onChange={this.handleInputChange} onPressEnter={this.login.bind(this)} />
                    </div>    
                    </fieldset>
                    <fieldset className="tblFooters">
                    <input className="btn btn-primary" value="Login" type="submit" id="input_go" onClick={this.login.bind(this)} />
                        <input className="btn btn-primary" value="passkey" type="submit" id="input_go" onClick={this.passkey.bind(this)} />
                    </fieldset>
                </div>
                <div className={this.state.loginStep === 'BIND'?'container':'hide'}>
                    <fieldset>
                        <legend>绑定OTP</legend>
                        <div className="item qrcode">
                        <label>
                        使用手机 Google Authenticator 应用扫描以下二维码<br></br>
                        <Tag icon={<AppleOutlined />} color="#000">
                            <a href="https://apps.apple.com/cn/app/google-authenticator/id388497605" target="view_window">iOS版本</a>
                        </Tag>
                        <Tag icon={<AndroidOutlined />} color="#3ddc84">
                            <a href="https://github.com/google/google-authenticator-android/releases" target="view_window">安卓版本</a>
                        </Tag>
                        
                        
                        </label>
                        <br></br>
                        <QRCode value={this.state.qrCode}></QRCode>
                        <br></br>
                        <label>Secret: {this.state.authSecret}</label>
                        </div>
                        <div className="item">
                        <Input prefix={<VerifiedOutlined />} placeholder="双因子动态码" name="otpPass" onChange={this.handleInputChange}  onPressEnter={this.bindOtp.bind(this)} />
                        </div>
                    </fieldset>
                    <fieldset className="tblFooters">
                        <input className="btn btn-primary" value="BIND" type="submit" id="input_go" onClick={this.bindOtp.bind(this)} />
                    </fieldset>
                </div>
                <div className={this.state.loginStep === 'VERIFY'?'container':'hide'}>
                <fieldset>
                        <legend>验证OTP</legend>
                        <div className="item">

                        
                        <Input prefix={<VerifiedOutlined />} placeholder="双因子动态码" name="otpPass" onChange={this.handleInputChange}  onPressEnter={this.verifyOtp.bind(this)} />

                        </div>
                    </fieldset>
                    <fieldset className="tblFooters">
                        <input className="btn btn-primary" value="Verify" type="submit" id="input_go" onClick={this.verifyOtp.bind(this)} />
                    </fieldset>
                </div>
            </div>

        )
    }
}

export default withRouter(Login);