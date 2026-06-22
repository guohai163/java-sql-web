export function getAccessTokenStatusMeta(status) {
  if (status === 'ACTIVE') {
    return { color: 'success', text: '有效' };
  }
  if (status === 'EXPIRED') {
    return { color: 'warning', text: '已过期' };
  }
  return { color: 'default', text: '未申请' };
}

export function isRpIdCompatible(rpId) {
  if (!rpId) {
    return true;
  }

  const hostname = window.location.hostname;
  return hostname === rpId || hostname.endsWith(`.${rpId}`);
}

export function buildPasskeyDomainMessage(rpId) {
  const currentHost = window.location.host;
  return `当前页面域名 ${currentHost} 与 passkey 依赖域 ${rpId} 不一致，浏览器不会拉起系统 passkey。请切换到 ${rpId} 对应站点再使用 passkey。`;
}

export function formatServerLabel(server) {
  const host = (server?.dbServerHost || '').trim();
  const name = (server?.dbServerName || '').trim();

  if (host && name) {
    return `${host}@${name}`;
  }
  return host || name || '未命名服务器';
}

export function matchServerKeyword(server, keyword) {
  const normalizedKeyword = (keyword || '').trim().toLowerCase();
  if (!normalizedKeyword) {
    return true;
  }

  const host = (server?.dbServerHost || '').toLowerCase();
  const name = (server?.dbServerName || '').toLowerCase();
  return host.includes(normalizedKeyword) || name.includes(normalizedKeyword);
}

export function normalizeArray(value) {
  return Array.isArray(value) ? value : [];
}

function compareAscii(left, right) {
  const leftValue = String(left ?? '');
  const rightValue = String(right ?? '');
  const minLength = Math.min(leftValue.length, rightValue.length);

  for (let index = 0; index < minLength; index += 1) {
    const charDiff = leftValue.charCodeAt(index) - rightValue.charCodeAt(index);
    if (charDiff !== 0) {
      return charDiff;
    }
  }

  return leftValue.length - rightValue.length;
}

export function sortTablesByAsciiName(list) {
  return normalizeArray(list)
    .slice()
    .sort((left, right) => compareAscii(left?.tableName, right?.tableName));
}
