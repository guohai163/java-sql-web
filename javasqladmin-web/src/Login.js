import React from 'react';
import './Login.css'

class Login extends React.Component {
    constructor(props){
        console.log('进入login')
        super(props)
        this.state = {
            userName: '',
            passWord: ''
        }
    }
    render(){
        return (
            <div className="container">
                <fieldset>
                <legend>
                <input type="hidden" name="set_session" value="c6f3567c92ddc6331b0a5bc426e1d20b" />登录</legend>
                <div className="item">
                <label for="input_username">用户名：</label>
                <input type="text" name="pma_username" id="input_username" size="24" class="textfield" />
                </div>
                <div class="item">
                <label for="input_password">密码：</label>
                <input type="password" name="pma_password" id="input_password" size="24" class="textfield" />
                </div>    
                </fieldset>
                <fieldset class="tblFooters">
                <input class="btn btn-primary" value="Login" type="submit" id="input_go" />
                </fieldset>
            </div>
        )
    }
}

export default Login