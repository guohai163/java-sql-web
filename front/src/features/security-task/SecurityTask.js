import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Input, Modal, Spin, Tag } from 'antd';
import {
  AndroidOutlined,
  AppleOutlined,
  LockOutlined,
  UserOutlined,
  VerifiedOutlined,
} from '@ant-design/icons';
import QRCode from 'qrcode.react';
import { createClient } from '@/shared/api/apiClient';
import '@/features/login/Login.css';
import './SecurityTask.css';

const { confirm } = Modal;

const initialState = {
  loading: true,
  submitting: false,
  otpSessionLoading: false,
  taskInfo: null,
  password: '',
  confirmPassword: '',
  otpPass: '',
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

function SecurityTask() {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState(initialState);

  const setStatePatch = (patch) => {
    setState((previous) => ({
      ...previous,
      ...(typeof patch === 'function' ? patch(previous) : patch),
    }));
  };

  const searchParams = new URLSearchParams(location.search);
  const uuid = searchParams.get('uuid') || '';

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
    if (!state.otpPass.trim()) {
      showDialog('请输入动态码');
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
        otpPass: state.otpPass,
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

  const renderPasswordStep = () => (
    <fieldset>
      <legend>
        <LockOutlined /> 设置密码
      </legend>
      <div className="item">
        <Input
          prefix={<UserOutlined />}
          value={state.taskInfo?.userName || ''}
          disabled
        />
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
      <div className="security-task-help">
        密码至少 8 位，且需包含大写字母、小写字母、数字、特殊字符中的 3 类。
      </div>
      <fieldset className="tblFooters">
        <Button loading={state.submitting} type="primary" onClick={submitPassword}>
          提交密码
        </Button>
      </fieldset>
    </fieldset>
  );

  const renderOtpStep = () => (
    <fieldset>
      <legend>
        <VerifiedOutlined /> 绑定OTP
      </legend>
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
        {state.taskInfo?.authSecret ? (
          <>
            <QRCode value={buildOtpUrl(state.taskInfo?.userName, state.taskInfo?.authSecret)} />
            <br />
            <label>Secret: {state.taskInfo?.authSecret}</label>
          </>
        ) : (
          <Spin />
        )}
      </div>
      <div className="item">
        <Input
          prefix={<VerifiedOutlined />}
          placeholder="双因子动态码"
          value={state.otpPass}
          onChange={(event) => {
            setStatePatch({
              otpPass: event.target.value,
            });
          }}
          onPressEnter={bindOtp}
        />
      </div>
      <fieldset className="tblFooters">
        <Button
          loading={state.submitting || state.otpSessionLoading}
          type="primary"
          onClick={bindOtp}
        >
          绑定OTP
        </Button>
      </fieldset>
    </fieldset>
  );

  return (
    <div className="center">
      <div className="container security-task-container">
        <fieldset>
          <legend>安全任务</legend>
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
              <div>用户名：{state.taskInfo.userName}</div>
              <div>邮箱：{state.taskInfo.email}</div>
              <div>
                当前任务：
                <Tag color={state.taskInfo.taskType === 'ACTIVATE' ? 'processing' : 'warning'}>
                  {state.taskInfo.taskType === 'ACTIVATE'
                    ? '激活账号'
                    : state.taskInfo.taskType === 'RESET_PASSWORD'
                      ? '重置密码'
                      : '重绑OTP'}
                </Tag>
              </div>
              <div>过期时间：{state.taskInfo.expireTime || '-'}</div>
            </div>
          ) : null}
        </fieldset>
        {!state.loading && !state.errorMessage && state.taskInfo?.taskStatus === 'PENDING_PASSWORD'
          ? renderPasswordStep()
          : null}
        {!state.loading && !state.errorMessage && state.taskInfo?.taskStatus === 'PENDING_OTP'
          ? renderOtpStep()
          : null}
      </div>
    </div>
  );
}

export default SecurityTask;
