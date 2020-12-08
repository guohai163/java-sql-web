import React from 'react';
import {withRouter} from "react-router-dom";
import './Login.css'
import FetchHttpClient, { json } from 'fetch-http-client';
import config from "./config";
import cookie from 'react-cookies'

class Login extends React.Component {
    constructor(props){
        super(props)
        this.state = {
            userName: '',
            passWord: ''
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
                    cookie.save('token', response.jsonData.data.token, {path: '/'})
                    this.props.history.push('/');
                }
            })
    }
    render(){
        return (
            <div className="container">
                <fieldset>
                <legend>
                <input type="hidden" name="set_session" value="c6f3567c92ddc6331b0a5bc426e1d20b" />登录</legend>
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
        )
    }
}

export default withRouter(Login);