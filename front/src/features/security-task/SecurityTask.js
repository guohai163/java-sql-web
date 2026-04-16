import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Input, Modal, Spin, Tag } from 'antd';
import {
  AndroidOutlined,
  AppleOutlined,
  LockOutlined,
  UserOutlined,
} from '@ant-design/icons';
import QRCode from 'qrcode.react';
import { createClient } from '@/shared/api/apiClient';
import '@/features/login/Login.css';
import './SecurityTask.css';

const { confirm } = Modal;
const OTP_LENGTH = 6;
const EMPTY_OTP_DIGITS = Array.from({ length: OTP_LENGTH }, () => '');

const initialState = {
  loading: true,
  submitting: false,
  otpSessionLoading: false,
  taskInfo: null,
  password: '',
  confirmPassword: '',
  otpDigits: [...EMPTY_OTP_DIGITS],
  errorMessage: '',
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

function getTaskTypeLabel(taskType) {
  if (taskType === 'ACTIVATE') {
    return '激活账号';
  }
  if (taskType === 'RESET_PASSWORD') {
    return '重置密码';
  }
  return '重绑OTP';
}

function SecurityTask() {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState(initialState);
  const loginLogo = '/jsw_logo.png';
  const otpInputRefs = useRef([]);

  const setStatePatch = (patch) => {
    setState((previous) => ({
      ...previous,
      ...(typeof patch === 'function' ? patch(previous) : patch),
    }));
  };

  const searchParams = new URLSearchParams(location.search);
  const uuid = searchParams.get('uuid') || '';

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

    focusOtpInput(Math.min(index + incomingDigits.length, OTP_LENGTH - 1));
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

  const loadTask = async () => {
    if (!uuid) {
      setStatePatch({
        loading: false,
        errorMessage: '链接缺少任务标识，请联系管理员重新生成。',
      });
      return;
    }

    setStatePatch({
      loading: true,
      errorMessage: '',
    });

    const client = createClient();
    const response = await client.get(`/user/security-task/${uuid}`, {
      headers: { 'Content-Type': 'application/json' },
    });

    if (response.jsonData.status) {
      setStatePatch({
        loading: false,
        taskInfo: response.jsonData.data,
        errorMessage: '',
        otpDigits: [...EMPTY_OTP_DIGITS],
      });
      return;
    }

    setStatePatch({
      loading: false,
      taskInfo: null,
      errorMessage: response.jsonData.message || '链接无效或已失效',
    });
  };

  const createOtpSession = async () => {
    if (!uuid) {
      return;
    }
    setStatePatch({
      otpSessionLoading: true,
      errorMessage: '',
    });
    const client = createClient();
    const response = await client.post(`/user/security-task/${uuid}/otp-session`, {
      headers: { 'Content-Type': 'application/json' },
    });

    if (response.jsonData.status) {
      setStatePatch({
        otpSessionLoading: false,
        taskInfo: response.jsonData.data,
        otpDigits: [...EMPTY_OTP_DIGITS],
      });
      return;
    }

    setStatePatch({
      otpSessionLoading: false,
      errorMessage: response.jsonData.message || 'OTP会话创建失败',
    });
  };

  useEffect(() => {
    void loadTask();
  }, [uuid]);

  useEffect(() => {
    if (
      state.loading ||
      state.otpSessionLoading ||
      state.taskInfo?.taskType !== 'RESET_OTP' ||
      state.taskInfo?.taskStatus !== 'PENDING_OTP' ||
      (state.taskInfo?.token && state.taskInfo?.authSecret)
    ) {
      return;
    }
    void createOtpSession();
  }, [state.loading, state.otpSessionLoading, state.taskInfo]);

  useEffect(() => {
    if (state.taskInfo?.taskStatus === 'PENDING_OTP') {
      focusOtpInput(0);
    }
  }, [state.taskInfo?.taskStatus]);

  const submitPassword = async () => {
    if (!state.password.trim()) {
      showDialog('请输入新密码');
      return;
    }
    if (state.password !== state.confirmPassword) {
      showDialog('两次输入的密码不一致');
      return;
    }

    setStatePatch({
      submitting: true,
      errorMessage: '',
    });

    const client = createClient();
    const response = await client.post(`/user/security-task/${uuid}/password`, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ passWord: state.password }),
    });

    if (response.jsonData.status) {
      const nextTaskInfo = response.jsonData.data;
      if (nextTaskInfo?.taskStatus === 'PENDING_OTP') {
        setStatePatch({
          submitting: false,
          taskInfo: nextTaskInfo,
          password: '',
          confirmPassword: '',
          otpDigits: [...EMPTY_OTP_DIGITS],
        });
        return;
      }
      navigate(`/login?username=${encodeURIComponent(nextTaskInfo?.userName || state.taskInfo?.userName || '')}`, {
        replace: true,
      });
      return;
    }

    setStatePatch({
      submitting: false,
      errorMessage: response.jsonData.message || '密码提交失败',
    });
  };

  const bindOtp = async () => {
    const otpPass = getOtpValue();
    if (otpPass.length !== OTP_LENGTH) {
      showDialog('请输入完整的 6 位动态码');
      return;
    }

    setStatePatch({
      submitting: true,
      errorMessage: '',
    });
    const client = createClient();
    const response = await client.post(`/user/security-task/${uuid}/bind-otp`, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        token: state.taskInfo?.token || '',
        otpPass,
      }),
    });

    if (response.jsonData.status) {
      navigate(`/login?username=${encodeURIComponent(state.taskInfo?.userName || '')}`, {
        replace: true,
      });
      return;
    }

    setStatePatch({
      submitting: false,
      errorMessage: response.jsonData.message || 'OTP绑定失败',
    });
  };

  const renderOtpInputs = () => (
    <div className="login-otp-group">
      <div className="login-otp-grid">
        {state.otpDigits.map((digit, index) => (
          <input
            key={`security-task-otp-${index}`}
            ref={(element) => {
              otpInputRefs.current[index] = element;
            }}
            className="login-otp-input"
            inputMode="numeric"
            autoComplete={index === 0 ? 'one-time-code' : 'off'}
            maxLength={1}
            value={digit}
            onChange={(event) => handleOtpDigitChange(index, event)}
            onKeyDown={(event) => handleOtpKeyDown(index, event)}
            onPaste={handleOtpPaste}
          />
        ))}
      </div>
      <div className="login-otp-help">输入 6 位动态码后会自动跳转，也支持直接粘贴整段验证码。</div>
    </div>
  );

  const renderPasswordStep = () => (
    <section className="login-panel security-task-panel-section">
      <div className="login-panel-head">
        <h1>设置密码</h1>
        <p>为账号设置新的登录密码，完成后即可继续后续安全流程。</p>
      </div>
      <div className="login-form">
        <div className="item">
          <Input prefix={<UserOutlined />} value={state.taskInfo?.userName || ''} disabled />
        </div>
        <div className="item">
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="请输入新密码"
            value={state.password}
            onChange={(event) => {
              setStatePatch({
                password: event.target.value,
              });
            }}
            onPressEnter={submitPassword}
          />
        </div>
        <div className="item">
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="请再次输入新密码"
            value={state.confirmPassword}
            onChange={(event) => {
              setStatePatch({
                confirmPassword: event.target.value,
              });
            }}
            onPressEnter={submitPassword}
          />
        </div>
      </div>
      <div className="security-task-help">
        密码至少 8 位，且需包含大写字母、小写字母、数字、特殊字符中的 3 类。
      </div>
      <div className="login-actions security-task-actions">
        <Button loading={state.submitting} type="primary" onClick={submitPassword}>
          提交密码
        </Button>
      </div>
    </section>
  );

  const renderOtpStep = () => (
    <section className="login-panel security-task-panel-section">
      <div className="login-panel-head">
        <h1>绑定 OTP</h1>
        <p>完成双因子绑定后，这个账号就可以重新恢复安全登录。</p>
      </div>
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
        {state.taskInfo?.authSecret ? (
          <>
            <QRCode value={buildOtpUrl(state.taskInfo?.userName, state.taskInfo?.authSecret)} />
            <div className="login-secret">Secret: {state.taskInfo?.authSecret}</div>
          </>
        ) : (
          <Spin />
        )}
      </div>
      <div className="security-task-otp-shell">{renderOtpInputs()}</div>
      <div className="login-actions security-task-actions">
        <Button
          loading={state.submitting || state.otpSessionLoading}
          type="primary"
          onClick={bindOtp}
        >
          绑定OTP
        </Button>
      </div>
    </section>
  );

  return (
    <div className="login-page security-task-page">
      <div className="login-card security-task-card">
        <div className="login-brand">
          <img src={loginLogo} alt="JavaSqlWeb" />
          <div className="login-brand-copy">
            <div className="login-brand-wordmark">
              <span className="tone-java">Java</span>
              <span className="tone-sql">Sql</span>
              <span className="tone-web">Web</span>
            </div>
            <span className="login-brand-subtitle">安全任务</span>
          </div>
        </div>

        <div className="login-panel security-task-panel">
          <div className="login-panel-head">
            <h1>安全任务</h1>
            <p>通过一次性的安全链接完成密码重置、账号激活或 OTP 重绑。</p>
          </div>

          {state.loading ? (
            <div className="security-task-loading">
              <Spin />
            </div>
          ) : null}

          {!state.loading && state.errorMessage ? (
            <Alert
              message={state.errorMessage}
              type="error"
              showIcon
              action={
                <Button size="small" onClick={() => navigate('/login')}>
                  返回登录
                </Button>
              }
            />
          ) : null}

          {!state.loading && !state.errorMessage && state.taskInfo ? (
            <div className="security-task-summary">
              <div className="security-task-summary-row">
                <span className="security-task-summary-label">用户名</span>
                <span className="security-task-summary-value">{state.taskInfo.userName}</span>
              </div>
              <div className="security-task-summary-row">
                <span className="security-task-summary-label">邮箱</span>
                <span className="security-task-summary-value">{state.taskInfo.email}</span>
              </div>
              <div className="security-task-summary-row">
                <span className="security-task-summary-label">当前任务</span>
                <Tag color={state.taskInfo.taskType === 'ACTIVATE' ? 'processing' : 'warning'}>
                  {getTaskTypeLabel(state.taskInfo.taskType)}
                </Tag>
              </div>
              <div className="security-task-summary-row">
                <span className="security-task-summary-label">过期时间</span>
                <span className="security-task-summary-value">{state.taskInfo.expireTime || '-'}</span>
              </div>
            </div>
          ) : null}

          {!state.loading && !state.errorMessage && state.taskInfo?.taskStatus === 'PENDING_PASSWORD'
            ? renderPasswordStep()
            : null}
          {!state.loading && !state.errorMessage && state.taskInfo?.taskStatus === 'PENDING_OTP'
            ? renderOtpStep()
            : null}
        </div>
      </div>
    </div>
  );
}

export default SecurityTask;
