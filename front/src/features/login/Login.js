import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import './Login.css';
import cookie from 'react-cookies';
import { Modal, Input, Tag } from 'antd';
import {
  AndroidOutlined,
  AppleOutlined,
  UnlockOutlined,
  UserOutlined,
  VerifiedOutlined,
} from '@ant-design/icons';
import * as webauthnJson from '@github/webauthn-json';
import QRCode from 'qrcode.react';
import { createClient } from '@/shared/api/apiClient';

const { confirm } = Modal;

const initialState = {
  userName: '',
  passWord: '',
  loginStep: 'LOGIN',
  authSecret: '',
  qrCode: '',
  otpPass: '',
  token: '',
};

function showDialog(content, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

function buildOtpUrl(userName, authSecret) {
  return `otpauth://totp/${userName}@${window.location.host}?secret=${authSecret}&issuer=JavaSqlWeb`;
}

function Login() {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState(initialState);

  const updateState = (patch) => {
    setState((previous) => ({
      ...previous,
      ...patch,
    }));
  };

  const handleInputChange = (event) => {
    const { name, value } = event.target;
    updateState({
      [name === 'username' ? 'userName' : name === 'password' ? 'passWord' : name]: value,
    });
  };

  const autoCreateUser = async (userName, timestamp, sign) => {
    const requestUrl = `/user/create_user/${userName}?timestamp=${timestamp}`;
    const client = createClient();
    const response = await client.post(requestUrl, {
      headers: { 'Content-Type': 'application/json', sign },
      body: JSON.stringify({ token: state.token, otpPass: state.otpPass }),
    });

    if (response.jsonData.status) {
      if (response.jsonData.data.authStatus === 'BINDING') {
        updateState({
          authSecret: response.jsonData.data.authSecret,
          loginStep: 'BIND',
          qrCode: buildOtpUrl(
            response.jsonData.data.userName,
            response.jsonData.data.authSecret,
          ),
          token: response.jsonData.data.token,
        });
      } else if (response.jsonData.data.authStatus === 'BIND') {
        updateState({
          loginStep: 'VERIFY',
          token: response.jsonData.data.token,
        });
      }
      return;
    }

    showDialog(`自动激活账号失败: ${response.jsonData.message}`);
  };

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const userName = searchParams.get('user_name');
    const timestamp = searchParams.get('timestamp');
    const sign = searchParams.get('sign');

    if (userName) {
      void autoCreateUser(userName, timestamp, sign);
    }
  }, [location.search]);

  const passkey = async () => {
    if (!webauthnJson.supported()) {
      showDialog('当前系统环境无法开启passKey功能');
      return;
    }

    try {
      const sessionKey = Math.random().toString(36).substring(2);
      const client = createClient();
      const response = await client.get('/webauthn/get', {
        headers: { 'Content-Type': 'application/json', 'Session-key': sessionKey },
      });
      const publicKeyCredential = await webauthnJson.get(
        JSON.parse(response.jsonData.data),
      );
      const signInResponse = await client.post('/webauthn/signin', {
        headers: { 'Content-Type': 'application/json', 'Session-key': sessionKey },
        body: JSON.stringify(publicKeyCredential),
      });

      if (signInResponse.jsonData.status) {
        cookie.save('token', signInResponse.jsonData.data.token, { path: '/' });
        navigate('/');
        return;
      }

      showDialog(signInResponse.jsonData.message);
    } catch (error) {
      showDialog('passKey 登录失败');
    }
  };

  const login = async () => {
    try {
      const client = createClient();
      const response = await client.post('/user/login', {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userName: state.userName,
          passWord: state.passWord,
        }),
      });

      if (response.status !== 200) {
        showDialog('服务器连接失败');
        return;
      }

      if (response.jsonData.status) {
        if (response.jsonData.data.authStatus === 'BINDING') {
          updateState({
            authSecret: response.jsonData.data.authSecret,
            loginStep: 'BIND',
            qrCode: buildOtpUrl(
              response.jsonData.data.userName,
              response.jsonData.data.authSecret,
            ),
            token: response.jsonData.data.token,
          });
          return;
        }

        if (response.jsonData.data.authStatus === 'BIND') {
          updateState({
            loginStep: 'VERIFY',
            token: response.jsonData.data.token,
          });
        }
        return;
      }

      showDialog(response.jsonData.message);
    } catch (error) {
      showDialog('服务器连接失败');
    }
  };

  const bindOtp = async () => {
    const client = createClient();
    const response = await client.post('/user/bindotp', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: state.token, otpPass: state.otpPass }),
    });

    if (response.jsonData.status) {
      cookie.save('token', state.token, { path: '/' });
      navigate('/');
    }
  };

  const verifyOtp = async () => {
    const client = createClient();
    const response = await client.post('/user/verifyotp', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: state.token, otpPass: state.otpPass }),
    });

    if (response.jsonData.status) {
      cookie.save('token', state.token, { path: '/' });
      navigate('/');
      return;
    }

    showDialog(response.jsonData.message);
  };

  return (
    <div className="center">
      <div className={state.loginStep === 'LOGIN' ? 'container' : 'hide'}>
        <fieldset>
          <legend>登录</legend>
          <div className="item">
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
              name="username"
              value={state.userName}
              onChange={handleInputChange}
            />
          </div>
          <div className="item">
            <Input.Password
              prefix={<UnlockOutlined />}
              placeholder="密码"
              name="password"
              value={state.passWord}
              onChange={handleInputChange}
              onPressEnter={login}
            />
          </div>
        </fieldset>
        <fieldset className="tblFooters">
          <input
            className="btn btn-primary"
            value="Login"
            type="submit"
            onClick={login}
          />
          <input
            className="btn btn-primary"
            value="passkey"
            type="submit"
            onClick={passkey}
          />
        </fieldset>
      </div>
      <div className={state.loginStep === 'BIND' ? 'container' : 'hide'}>
        <fieldset>
          <legend>绑定OTP</legend>
          <div className="item qrcode">
            <label>
              使用手机 Google Authenticator 应用扫描以下二维码
              <br />
              <Tag icon={<AppleOutlined />} color="#000">
                <a
                  href="https://apps.apple.com/cn/app/google-authenticator/id388497605"
                  rel="noreferrer"
                  target="view_window"
                >
                  iOS版本
                </a>
              </Tag>
              <Tag icon={<AndroidOutlined />} color="#3ddc84">
                <a
                  href="https://github.com/google/google-authenticator-android/releases"
                  rel="noreferrer"
                  target="view_window"
                >
                  安卓版本
                </a>
              </Tag>
            </label>
            <br />
            <QRCode value={state.qrCode} />
            <br />
            <label>Secret: {state.authSecret}</label>
          </div>
          <div className="item">
            <Input
              prefix={<VerifiedOutlined />}
              placeholder="双因子动态码"
              name="otpPass"
              value={state.otpPass}
              onChange={handleInputChange}
              onPressEnter={bindOtp}
            />
          </div>
        </fieldset>
        <fieldset className="tblFooters">
          <input
            className="btn btn-primary"
            value="BIND"
            type="submit"
            onClick={bindOtp}
          />
        </fieldset>
      </div>
      <div className={state.loginStep === 'VERIFY' ? 'container' : 'hide'}>
        <fieldset>
          <legend>验证OTP</legend>
          <div className="item">
            <Input
              prefix={<VerifiedOutlined />}
              placeholder="双因子动态码"
              name="otpPass"
              value={state.otpPass}
              onChange={handleInputChange}
              onPressEnter={verifyOtp}
            />
          </div>
        </fieldset>
        <fieldset className="tblFooters">
          <input
            className="btn btn-primary"
            value="Verify"
            type="submit"
            onClick={verifyOtp}
          />
        </fieldset>
      </div>
    </div>
  );
}

export default Login;
