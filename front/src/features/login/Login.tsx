import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import './Login.css';
import cookie from 'react-cookies';
import { Alert, Button, Divider, Modal, Input, Tag } from 'antd';
import {
  AndroidOutlined,
  AppleOutlined,
  LoginOutlined,
  UnlockOutlined,
  UserOutlined,
} from '@ant-design/icons';
import * as webauthnJson from '@github/webauthn-json';
import QRCode from 'qrcode.react';
import { createClient } from '@/shared/api/apiClient';

const { confirm } = Modal;
const OTP_LENGTH = 6;
const EMPTY_OTP_DIGITS = Array.from({ length: OTP_LENGTH }, () => '');

interface LoginNotice {
  type: 'success' | 'warning' | 'error' | 'info';
  message: string;
}

interface LoginState {
  userName: string;
  passWord: string;
  loginStep: string;
  authSecret: string;
  qrCode: string;
  otpDigits: string[];
  token: string;
  notice: LoginNotice | null;
}

const initialState: LoginState = {
  userName: '',
  passWord: '',
  loginStep: 'LOGIN',
  authSecret: '',
  qrCode: '',
  otpDigits: [...EMPTY_OTP_DIGITS],
  token: '',
  notice: null,
};

function showDialog(content: string, title = '提示') {
  confirm({
    title,
    content,
    onOk() {},
    onCancel() {},
  });
}

function buildOtpUrl(userName: string, authSecret: string): string {
  return `otpauth://totp/${userName}@${window.location.host}?secret=${authSecret}&issuer=JavaSqlWeb`;
}

function isRpIdCompatible(rpId: string | undefined): boolean {
  if (!rpId) {
    return true;
  }

  const hostname = window.location.hostname;
  return hostname === rpId || hostname.endsWith(`.${rpId}`);
}

function buildPasskeyDomainMessage(rpId: string): string {
  const currentHost = window.location.host;
  return `当前页面域名 ${currentHost} 与 passkey 依赖域 ${rpId} 不一致，浏览器不会拉起系统 passkey。请切换到 ${rpId} 对应站点再使用 passkey。`;
}

function buildOidcErrorMessage(error: string | null, errorDescription: string | null): string {
  if (errorDescription && errorDescription.trim()) {
    return `${errorDescription.trim()}。你可以重新发起 OIDC 登录或使用账号密码登录。`;
  }
  if (error === 'access_denied') {
    return '你已取消本次 OIDC 授权登录。你可以重新发起 OIDC 登录或使用账号密码登录。';
  }
  return 'OIDC 授权失败，请稍后重试。你可以重新发起 OIDC 登录或使用账号密码登录。';
}

function Login() {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState<LoginState>(initialState);
  const [oidcEnabled, setOidcEnabled] = useState(false);
  const [oidcLoading, setOidcLoading] = useState(false);
  const loginLogo = '/jsw_logo.png';
  const otpInputRefs = useRef<(HTMLInputElement | null)[]>([]);

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
      notice: null,
    });
  };

  const setNotice = (message, type = 'error') => {
    updateState({
      notice: message
        ? {
            type,
            message,
          }
        : null,
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
    const oidcError = searchParams.get('oidc_error');
    const oidcErrorDescription = searchParams.get('oidc_error_description');

    if (oidcError) {
      updateState({
        loginStep: 'LOGIN',
        token: '',
        authSecret: '',
        qrCode: '',
        otpDigits: [...EMPTY_OTP_DIGITS],
        notice: {
          type: 'warning',
          message: buildOidcErrorMessage(oidcError, oidcErrorDescription),
        },
      });
      window.history.replaceState({}, '', '/login');
      return;
    }

    // 处理 OIDC 登录回调 token
    const oidcToken = searchParams.get('oidc_token');
    if (oidcToken) {
      const authStatusParam = searchParams.get('auth_status');

      if (authStatusParam === 'BINDING') {
        // 需要绑定 OTP
        const authSecret = searchParams.get('auth_secret') || '';
        const oidcUserName = searchParams.get('user_name') || '';
        updateState({
          loginStep: 'BIND',
          token: oidcToken,
          authSecret,
          qrCode: buildOtpUrl(oidcUserName, authSecret),
          otpDigits: [...EMPTY_OTP_DIGITS],
          notice: null,
        });
        // 清除 URL 参数但留在登录页
        window.history.replaceState({}, '', '/login');
        return;
      }

      if (authStatusParam === 'BIND') {
        // 需要验证 OTP
        updateState({
          loginStep: 'VERIFY',
          token: oidcToken,
          otpDigits: [...EMPTY_OTP_DIGITS],
          notice: null,
        });
        // 清除 URL 参数但留在登录页
        window.history.replaceState({}, '', '/login');
        return;
      }

      // 其他状态（已完成登录），直接进入
      cookie.save('token', oidcToken, { path: '/' });
      window.history.replaceState({}, '', '/');
      navigate('/');
      return;
    }

    if (userName) {
      updateState({
        userName,
      });
    }

    // 检查 OIDC 登录是否可用
    const checkOidc = async () => {
      try {
        const client = createClient();
        const res = await client.get('/api/oidc/login-enabled', {
          headers: { 'Content-Type': 'application/json' },
        });
        if (res.jsonData?.status && res.jsonData?.data?.enabled) {
          setOidcEnabled(true);
        }
      } catch (e) {
        // OIDC not available, silently ignore
      }
    };
    void checkOidc();
  }, [location.search]);

  useEffect(() => {
    if (state.loginStep === 'BIND' || state.loginStep === 'VERIFY') {
      focusOtpInput(0);
    }
  }, [state.loginStep]);

  const passkey = async () => {
    if (!webauthnJson.supported()) {
      setNotice('当前系统环境无法开启 passkey 功能');
      showDialog('当前系统环境无法开启passKey功能');
      return;
    }

    try {
      const sessionKey = Math.random().toString(36).substring(2);
      const client = createClient();
      const response = await client.get('/webauthn/get', {
        headers: { 'Content-Type': 'application/json', 'Session-key': sessionKey },
      });

      if (response.jsonData.status !== true) {
        const message = response.jsonData.message || response.jsonData.data || 'passKey 登录准备失败';
        setNotice(message);
        showDialog(message);
        return;
      }

      const requestJson = JSON.parse(response.jsonData.data);
      const rpId = requestJson?.publicKey?.rpId;
      if (!isRpIdCompatible(rpId)) {
        const message = buildPasskeyDomainMessage(rpId);
        setNotice(message);
        showDialog(message, 'passkey 域名不匹配');
        return;
      }

      const publicKeyCredential = await webauthnJson.get(requestJson);
      const signInResponse = await client.post('/webauthn/signin', {
        headers: { 'Content-Type': 'application/json', 'Session-key': sessionKey },
        body: JSON.stringify(publicKeyCredential),
      });

      if (signInResponse.jsonData.status) {
        cookie.save('token', signInResponse.jsonData.data.token, { path: '/' });
        navigate('/');
        return;
      }

      setNotice(signInResponse.jsonData.message || 'passKey 登录失败');
      showDialog(signInResponse.jsonData.message || 'passKey 登录失败');
    } catch (error) {
      const message = error?.message || 'passKey 登录失败';
      setNotice(message);
      showDialog(message);
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
        setNotice('服务器连接失败');
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
            notice: null,
          });
          return;
        }

        if (response.jsonData.data.authStatus === 'BIND') {
          updateState({
            loginStep: 'VERIFY',
            otpDigits: [...EMPTY_OTP_DIGITS],
            token: response.jsonData.data.token,
            notice: null,
          });
        }
        return;
      }

      setNotice(response.jsonData.message || response.jsonData.data || '登录失败');
      showDialog(response.jsonData.message || response.jsonData.data || '登录失败');
    } catch (error) {
      setNotice('服务器连接失败');
      showDialog('服务器连接失败');
    }
  };

  const bindOtp = async () => {
    const otpPass = getOtpValue();
    if (otpPass.length !== OTP_LENGTH) {
      setNotice('请输入完整的 6 位双因子动态码');
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
      return;
    }

    setNotice(response.jsonData.message || response.jsonData.data || 'OTP 绑定失败');
    showDialog(response.jsonData.message || response.jsonData.data || 'OTP 绑定失败');
  };

  const verifyOtp = async () => {
    const otpPass = getOtpValue();
    if (otpPass.length !== OTP_LENGTH) {
      setNotice('请输入完整的 6 位双因子动态码');
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

    setNotice(response.jsonData.message || response.jsonData.data || 'OTP 校验失败');
    showDialog(response.jsonData.message || response.jsonData.data || 'OTP 校验失败');
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
          {state.notice ? (
            <Alert
              className="login-notice"
              showIcon
              type={state.notice.type}
              message={state.notice.message}
            />
          ) : null}
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
          {oidcEnabled && (
            <>
              <Divider plain style={{ margin: '16px 0 12px', color: '#999', fontSize: 12 }}>或</Divider>
              <div className="login-actions">
                <Button
                  block
                  icon={<LoginOutlined />}
                  loading={oidcLoading}
                  onClick={async () => {
                    setOidcLoading(true);
                    try {
                      const client = createClient();
                      const res = await client.get('/api/oidc/login-url', {
                        headers: { 'Content-Type': 'application/json' },
                      });
                      if (res.jsonData?.status && res.jsonData?.data?.authUrl) {
                        window.location.href = res.jsonData.data.authUrl;
                      } else {
                        setNotice(res.jsonData?.message || 'OIDC 登录不可用');
                      }
                    } catch {
                      setNotice('OIDC 登录请求失败');
                    } finally {
                      setOidcLoading(false);
                    }
                  }}
                  style={{
                    background: 'linear-gradient(135deg, #722ed1, #1890ff)',
                    color: '#fff',
                    border: 'none',
                    borderRadius: 8,
                    height: 40,
                    fontWeight: 600,
                  }}
                >
                  使用 OIDC 登录
                </Button>
              </div>
            </>
          )}
        </div>
        <div className={state.loginStep === 'BIND' ? 'login-panel' : 'hide'}>
          <div className="login-panel-head">
            <h1>绑定 OTP</h1>
            <p>首次登录需要完成双因子绑定，保护你的账号安全。</p>
          </div>
          {state.notice ? (
            <Alert
              className="login-notice"
              showIcon
              type={state.notice.type}
              message={state.notice.message}
            />
          ) : null}
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
          {state.notice ? (
            <Alert
              className="login-notice"
              showIcon
              type={state.notice.type}
              message={state.notice.message}
            />
          ) : null}
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
