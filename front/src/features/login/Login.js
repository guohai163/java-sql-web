import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import './Login.css';
import cookie from 'react-cookies';
import { Button, Modal, Input, Tag } from 'antd';
import {
  AndroidOutlined,
  AppleOutlined,
  UnlockOutlined,
  UserOutlined,
} from '@ant-design/icons';
import * as webauthnJson from '@github/webauthn-json';
import QRCode from 'qrcode.react';
import { createClient } from '@/shared/api/apiClient';

const { confirm } = Modal;
const OTP_LENGTH = 6;
const EMPTY_OTP_DIGITS = Array.from({ length: OTP_LENGTH }, () => '');

const initialState = {
  userName: '',
  passWord: '',
  loginStep: 'LOGIN',
  authSecret: '',
  qrCode: '',
  otpDigits: [...EMPTY_OTP_DIGITS],
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
  const loginLogo = '/jsw_logo.png';
  const otpInputRefs = useRef([]);

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

  const focusOtpInput = (index) => {
    window.requestAnimationFrame(() => {
      otpInputRefs.current[index]?.focus();
      otpInputRefs.current[index]?.select();
    });
  };

  const updateOtpDigits = (updater) => {
    setState((previous) => {
      const nextDigits =
        typeof updater === 'function' ? updater([...previous.otpDigits]) : updater;
      return {
        ...previous,
        otpDigits: nextDigits,
      };
    });
  };

  const getOtpValue = () => state.otpDigits.join('');

  const handleOtpDigitChange = (index, event) => {
    const incomingDigits = event.target.value.replace(/\D/g, '');

    if (incomingDigits === '') {
      updateOtpDigits((previousDigits) => {
        previousDigits[index] = '';
        return previousDigits;
      });
      return;
    }

    updateOtpDigits((previousDigits) => {
      let nextIndex = index;
      incomingDigits.split('').forEach((digit) => {
        if (nextIndex < OTP_LENGTH) {
          previousDigits[nextIndex] = digit;
          nextIndex += 1;
        }
      });
      return previousDigits;
    });

    const targetIndex = Math.min(index + incomingDigits.length, OTP_LENGTH - 1);
    if (targetIndex < OTP_LENGTH) {
      focusOtpInput(targetIndex);
    }
  };

  const handleOtpKeyDown = (index, event) => {
    if (event.key === 'Backspace') {
      if (state.otpDigits[index]) {
        event.preventDefault();
        updateOtpDigits((previousDigits) => {
          previousDigits[index] = '';
          return previousDigits;
        });
        return;
      }

      if (index > 0) {
        event.preventDefault();
        updateOtpDigits((previousDigits) => {
          previousDigits[index - 1] = '';
          return previousDigits;
        });
        focusOtpInput(index - 1);
      }
      return;
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      focusOtpInput(index - 1);
      return;
    }

    if (event.key === 'ArrowRight' && index < OTP_LENGTH - 1) {
      event.preventDefault();
      focusOtpInput(index + 1);
    }
  };

  const handleOtpPaste = (event) => {
    const pastedDigits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH);
    if (pastedDigits === '') {
      return;
    }

    event.preventDefault();
    updateOtpDigits(() => {
      const nextDigits = [...EMPTY_OTP_DIGITS];
      pastedDigits.split('').forEach((digit, index) => {
        nextDigits[index] = digit;
      });
      return nextDigits;
    });
    focusOtpInput(Math.min(pastedDigits.length, OTP_LENGTH) - 1);
  };

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const userName = searchParams.get('username');

    if (userName) {
      updateState({
        userName,
      });
    }
  }, [location.search]);

  useEffect(() => {
    if (state.loginStep === 'BIND' || state.loginStep === 'VERIFY') {
      focusOtpInput(0);
    }
  }, [state.loginStep]);

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
            otpDigits: [...EMPTY_OTP_DIGITS],
            token: response.jsonData.data.token,
          });
          return;
        }

        if (response.jsonData.data.authStatus === 'BIND') {
          updateState({
            loginStep: 'VERIFY',
            otpDigits: [...EMPTY_OTP_DIGITS],
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
    const otpPass = getOtpValue();
    if (otpPass.length !== OTP_LENGTH) {
      showDialog('请输入完整的 6 位双因子动态码');
      return;
    }

    const client = createClient();
    const response = await client.post('/user/bindotp', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: state.token, otpPass }),
    });

    if (response.jsonData.status) {
      cookie.save('token', state.token, { path: '/' });
      navigate('/');
    }
  };

  const verifyOtp = async () => {
    const otpPass = getOtpValue();
    if (otpPass.length !== OTP_LENGTH) {
      showDialog('请输入完整的 6 位双因子动态码');
      return;
    }

    const client = createClient();
    const response = await client.post('/user/verifyotp', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: state.token, otpPass }),
    });

    if (response.jsonData.status) {
      cookie.save('token', state.token, { path: '/' });
      navigate('/');
      return;
    }

    showDialog(response.jsonData.message);
  };

  const renderOtpInputs = () => (
    <div className="login-otp-group">
      <div className="login-otp-grid">
        {state.otpDigits.map((digit, index) => (
          <input
            key={`otp-${index}`}
            ref={(element) => {
              otpInputRefs.current[index] = element;
            }}
            autoComplete={index === 0 ? 'one-time-code' : 'off'}
            className="login-otp-input"
            inputMode="numeric"
            maxLength={1}
            value={digit}
            onChange={(event) => handleOtpDigitChange(index, event)}
            onKeyDown={(event) => handleOtpKeyDown(index, event)}
            onPaste={handleOtpPaste}
          />
        ))}
      </div>
      <div className="login-otp-help">输入一个数字后会自动跳到下一格，也支持直接粘贴 6 位验证码。</div>
    </div>
  );

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-brand">
          <img src={loginLogo} alt="JavaSqlWeb logo" />
          <div className="login-brand-copy">
            <strong className="login-brand-wordmark">
              <span className="tone-java">Java</span>
              <span className="tone-sql">Sql</span>
              <span className="tone-web">Web</span>
            </strong>
            <span className="login-brand-subtitle">安全登录</span>
          </div>
        </div>
        <div className={state.loginStep === 'LOGIN' ? 'login-panel' : 'hide'}>
          <div className="login-panel-head">
            <h1>登录</h1>
            <p>使用账号密码或 passkey 进入 JavaSqlWeb 工作台。</p>
          </div>
          <div className="login-form">
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
          </div>
          <div className="login-actions">
            <Button type="primary" onClick={login}>
              Login
            </Button>
            <Button onClick={passkey}>passkey</Button>
          </div>
        </div>
        <div className={state.loginStep === 'BIND' ? 'login-panel' : 'hide'}>
          <div className="login-panel-head">
            <h1>绑定 OTP</h1>
            <p>首次登录需要完成双因子绑定，保护你的账号安全。</p>
          </div>
          <div className="login-form">
            <div className="item qrcode">
              <div className="login-qrcode-copy">
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
              </div>
            <br />
            <QRCode value={state.qrCode} />
            <br />
              <div className="login-secret">Secret: {state.authSecret}</div>
          </div>
            {renderOtpInputs()}
          </div>
          <div className="login-actions">
            <Button type="primary" onClick={bindOtp}>
              BIND
            </Button>
          </div>
        </div>
        <div className={state.loginStep === 'VERIFY' ? 'login-panel' : 'hide'}>
          <div className="login-panel-head">
            <h1>验证 OTP</h1>
            <p>输入当前双因子动态码，继续进入系统。</p>
          </div>
          <div className="login-form">{renderOtpInputs()}</div>
          <div className="login-actions">
            <Button type="primary" onClick={verifyOtp}>
              Verify
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Login;
