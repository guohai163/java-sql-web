import React from 'react';
import {withRouter} from "react-router-dom";
import './Login.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import cookie from 'react-cookies'
import QRCode from 'qrcode.react'
import Dialog from 'rc-dialog';

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
    handleInputChange(event){
        console.log(event)
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
    login() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/user/login',{headers: { 'Content-Type': 'application/json' },
            body:JSON.stringify({userName: this.state.userName, passWord: this.state.passWord})})
            .then(response => {
                console.log(response.jsonData)
                if(response.jsonData.status){
                    //
                    // cookie.save('token', response.jsonData.data.token, {path: '/'})
                    // this.props.history.push('/');
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
            })
    }
    bindOtp() {
        const client = new FetchHttpClient(config.serverDomain);
        client.addMiddleware(json());
        client.post('/user/bindotp',{headers: { 'Content-Type': 'application/json' },
        body:JSON.stringify({token: this.state.token, otpPass: this.state.otpPass})})
        .then(response => {
            console.log(response.jsonData)
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
            console.log(response.jsonData)
            if(response.jsonData.status){
                cookie.save('token', this.state.token, {path: '/'})
                this.props.history.push('/');
            }
        })
    }
    render(){
        return (
            <div>
                {
                <Dialog title="aa" style={{ width: 600 }} visible>
                    <p>first dialog</p>
                </Dialog>
                }
                <div className={this.state.loginStep === 'LOGIN'?'container':'hide'}>
                    <fieldset>
                    <legend>登录</legend>
                    <div className="item">
                    <label for="input_username">用户名：</label>
                    <input type="text" name="username" id="input_username" size="24" class="textfield" onChange={this.handleInputChange} />
                    </div>
                    <div class="item">
                    <label for="input_password">密码：</label>
                    <input type="password" name="password" id="input_password" size="24" class="textfield" onChange={this.handleInputChange} />
                    </div>    
                    </fieldset>
                    <fieldset class="tblFooters">
                    <input class="btn btn-primary" value="Login" type="submit" id="input_go" onClick={this.login.bind(this)} />
                    </fieldset>
                </div>
                <div className={this.state.loginStep === 'BIND'?'container':'hide'}>
                    <fieldset>
                        <legend>绑定OTP</legend>
                        <div className="item qrcode">
                        <label>
                        使用手机 Google Authenticator 应用扫描以下二维码<br></br>获取6位验证码
                        </label>
                        <br></br>
                        <QRCode value={this.state.qrCode}></QRCode>
                        <br></br>
                        <label>Secret: {this.state.authSecret}</label>
                        </div>
                        <div className="item">
                        <label for="input_password">双因子动态码：</label>
                        <input type="text" name="otpPass" size="24" class="textfield" onChange={this.handleInputChange} />
                        </div>
                    </fieldset>
                    <fieldset class="tblFooters">
                        <input class="btn btn-primary" value="BIND" type="submit" id="input_go" onClick={this.bindOtp.bind(this)} />
                    </fieldset>
                </div>
                <div className={this.state.loginStep === 'VERIFY'?'container':'hide'}>
                <fieldset>
                        <legend>验证OTP</legend>
                        <div className="item">
                        <label for="input_password">双因子动态码：</label>
                        <input type="text" name="otpPass" size="24" class="textfield" onChange={this.handleInputChange} />
                        </div>
                    </fieldset>
                    <fieldset class="tblFooters">
                        <input class="btn btn-primary" value="Verify" type="submit" id="input_go" onClick={this.verifyOtp.bind(this)} />
                    </fieldset>
                </div>
            </div>

        )
    }
}

export default withRouter(Login);