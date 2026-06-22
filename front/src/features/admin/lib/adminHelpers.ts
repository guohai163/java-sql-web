export function getServerTestDetail(response, fallback = '测试失败，请检查服务器配置。') {
  const candidate = response?.jsonData?.data || response?.jsonData?.message || fallback;
  if (typeof candidate !== 'string') {
    return fallback;
  }

  const normalized = candidate.trim();
  return normalized || fallback;
}

export function summarizeServerTestMessage(detail, fallback) {
  const normalized = (detail || fallback || '').replace(/\s+/g, ' ').trim();
  if (!normalized) {
    return fallback || '';
  }
  if (normalized.length <= 28) {
    return normalized;
  }
  return `${normalized.slice(0, 28)}...`;
}

export function createServerTestResult(status, messageText, detail) {
  return {
    status,
    message: messageText,
    detail,
    testedAt:
      status === 'testing'
        ? ''
        : new Date().toLocaleString('zh-CN', { hour12: false }),
  };
}

export function pruneServerTestResults(connList, serverTestResults) {
  const validCodes = new Set((connList || []).map((item) => item.code));
  return Object.entries(serverTestResults || {}).reduce((accumulator, [serverCode, result]) => {
    if (validCodes.has(Number(serverCode)) || validCodes.has(serverCode)) {
      accumulator[serverCode] = result;
    }
    return accumulator;
  }, {});
}

export function buildServerRuntimeMap(runtimeList) {
  return (runtimeList || []).reduce((accumulator, item) => {
    if (item?.serverCode != null) {
      accumulator[item.serverCode] = item;
    }
    return accumulator;
  }, {});
}

export function buildConnListQuery(keyword, serverType) {
  const query = new URLSearchParams();
  const normalizedKeyword = (keyword || '').trim();
  const normalizedType = (serverType || '').trim();

  if (normalizedKeyword) {
    query.set('keyword', normalizedKeyword);
    query.set('dbName', normalizedKeyword);
  }
  if (normalizedType && normalizedType !== 'all') {
    query.set('serverType', normalizedType);
  }

  return query.toString();
}

export function summarizeSyncResult(result) {
  const failures = Array.isArray(result?.failures) ? result.failures : [];
  const summaryLines = [
    `总实例数：${result?.totalServers ?? 0}`,
    `成功：${result?.successCount ?? 0}`,
    `失败：${result?.failCount ?? 0}`,
    `同步时间：${result?.syncedAt || '-'}`,
  ];

  if (failures.length === 0) {
    return summaryLines.join('\n');
  }

  const failureLines = failures.map((failure) => {
    const serverName = failure?.serverName || '-';
    const serverCode = failure?.serverCode == null ? '-' : failure.serverCode;
    const detail = failure?.message || '同步失败';
    return `${serverName}(${serverCode})：${detail}`;
  });

  return `${summaryLines.join('\n')}\n失败明细：\n${failureLines.join('\n')}`;
}

export function getServerRuntimeMeta(status) {
  switch (status) {
    case 'ok':
      return { color: 'success', text: '正常' };
    case 'warning':
      return { color: 'warning', text: '警告' };
    case 'cooldown':
      return { color: 'error', text: '冷却中' };
    default:
      return { color: 'default', text: '未使用' };
  }
}

export function getAccountStatusMeta(status) {
  if (status === 'ACTIVE') {
    return { color: 'success', text: '正常' };
  }
  if (status === 'PENDING_ACTIVATION') {
    return { color: 'processing', text: '待激活' };
  }
  if (status === 'PENDING_PASSWORD_RESET') {
    return { color: 'warning', text: '待重置密码' };
  }
  if (status === 'PENDING_OTP_RESET') {
    return { color: 'warning', text: '待重绑OTP' };
  }
  return { color: 'default', text: status || '未知' };
}

export function getPendingTaskMeta(taskType) {
  if (taskType === 'ACTIVATE') {
    return { color: 'processing', text: '激活' };
  }
  if (taskType === 'RESET_PASSWORD') {
    return { color: 'warning', text: '重置密码' };
  }
  if (taskType === 'RESET_OTP') {
    return { color: 'warning', text: '重绑OTP' };
  }
  return { color: 'default', text: '无' };
}

export function getAuthStatusMeta(status) {
  if (status === 'BIND') {
    return { color: 'success', text: '已绑定' };
  }
  if (status === 'BINDING') {
    return { color: 'processing', text: '绑定中' };
  }
  return { color: 'default', text: status || '未绑定' };
}

export function createEmptyQueryLogCursor(pageSize = 25) {
  return {
    items: [],
    pageSize,
    firstCode: null,
    lastCode: null,
    hasOlder: false,
    hasNewer: false,
  };
}
