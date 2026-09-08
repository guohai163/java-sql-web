/**
 * 通用 API 响应类型，对应后端 Result<T>
 */
export interface ApiResult<T = unknown> {
  status: boolean;
  message: string;
  data: T;
}

/**
 * 通用 API 响应（含 HTTP 状态）
 */
export interface ApiResponse<T = unknown> {
  status: number;
  jsonData: ApiResult<T>;
}

// ── 用户相关 ──────────────────────────────────────

export interface UserBean {
  code?: number;
  userName: string;
  passWord?: string;
  token?: string;
  otpPass?: string;
  authStatus?: 'BIND' | 'BINDING' | string;
  authSecret?: string;
  accountStatus?: string;
  email?: string;
  groupName?: string;
  pendingTaskType?: string;
  pendingTaskUuid?: string;
  accessToken?: string;
  accessTokenExpireTime?: string;
  lastLoginTime?: string;
  createdAt?: string;
}

// ── 服务器 / 连接 ────────────────────────────────

export interface ConnectConfigBean {
  code: number;
  dbServerName: string;
  dbServerHost: string;
  dbServerPort: number;
  dbServerType: string;
  dbServerUsername: string;
  dbServerPassword?: string;
  dbGroup: string;
  dbSslMode?: string;
  databaseNames?: string;
}

export interface PoolStatBean {
  serverCode: number;
  status: string;
  activeCount?: number;
  idleCount?: number;
  maxPoolSize?: number;
  cooldownExpireTime?: string;
}

export interface TargetSessionStatBean {
  serverCode: number;
  platformUserName?: string;
  databaseUserName?: string;
  sessionId?: number;
  databaseName?: string;
  sessionStatus?: string;
  commandOrWait?: string;
  runningSeconds?: number;
  queryStartTime?: string;
  sqlText?: string;
  queryLogCode?: number;
}

// ── 查询日志 ──────────────────────────────────────

export interface QueryLogBean {
  code: number;
  userName: string;
  serverName: string;
  serverCode: number;
  dbName: string;
  sqlText: string;
  resultCount: number;
  queryTime: number;
  createDate: string;
}

export interface QueryLogCursorResponse {
  items: QueryLogBean[];
  pageSize: number;
  firstCode: number | null;
  lastCode: number | null;
  hasOlder: boolean;
  hasNewer: boolean;
}

// ── Dashboard ─────────────────────────────────────

export interface DashboardTrendPoint {
  label: string;
  count: number;
}

export interface DashboardUserRankingItem {
  userName: string;
  queryCount: number;
  avgDuration: number;
}

export interface DashboardObjectHotspotItem {
  objectName: string;
  queryCount: number;
}

export interface DashboardRecentQueryItem {
  userName: string;
  serverName: string;
  dbName: string;
  sqlText: string;
  resultCount: number;
  queryTime: number;
  createDate: string;
}

export interface DashboardSummary {
  totalUsers: number;
  totalServers: number;
  totalQueries: number;
  avgDuration: number;
  serverTypeBreakdown: Record<string, number>;
}

export interface DashboardResponse {
  summary: DashboardSummary;
  trend: DashboardTrendPoint[];
  userRanking: DashboardUserRankingItem[];
  dbHotspots: DashboardObjectHotspotItem[];
  tableHotspots: DashboardObjectHotspotItem[];
  recentQueries: DashboardRecentQueryItem[];
}

// ── 权限 ──────────────────────────────────────────

export interface UsergroupBean {
  code: number;
  groupName: string;
  groupDesc?: string;
}

export interface DbPermissionBean {
  groupCode: number;
  groupName: string;
  serverCount?: number;
}

// ── 安全任务 ──────────────────────────────────────

export interface SecurityTaskInfo {
  userName: string;
  email?: string;
  taskType: 'ACTIVATE' | 'RESET_PASSWORD' | 'RESET_OTP';
  taskStatus: 'PENDING_PASSWORD' | 'PENDING_OTP' | 'COMPLETED' | 'EXPIRED';
  expireTime?: string;
  token?: string;
  authSecret?: string;
}

// ── 同步结果 ──────────────────────────────────────

export interface ServerDatabaseSyncFailure {
  serverCode: number;
  serverName: string;
  message: string;
}

export interface ServerDatabaseSyncResult {
  totalServers: number;
  successCount: number;
  failCount: number;
  syncedAt: string;
  failures: ServerDatabaseSyncFailure[];
}

// ── 链接签发 ──────────────────────────────────────

export interface LinkIssueResult {
  linkUrl: string;
  expireTime: string;
  taskType: string;
}

// ── SQL 指南 ──────────────────────────────────────

export interface SqlGuidBean {
  title: string;
  script: string;
  server: string;
  database: string;
  category: string;
}

// ── 工作台 Dashboard ─────────────────────────────

export interface WorkbenchDashboardItem {
  key: string;
  label: string;
  value: string;
  unit?: string;
  status: string;
  message?: string;
}

export interface WorkbenchDashboardSection {
  key: string;
  title: string;
  status: string;
  items: WorkbenchDashboardItem[];
}

export interface WorkbenchDashboardResponse {
  sections: WorkbenchDashboardSection[];
  updatedAt: string;
}

export interface VannaGenerateSqlResponse {
  needsClarification: boolean;
  clarificationQuestion?: string | null;
  sql?: string | null;
  dialect: string;
  summary: string;
  matchedTables: string[];
  warnings: string[];
}

// ── OIDC / SSF ────────────────────────────────────

export interface OidcTokenInfo {
  connected: boolean;
  accessToken?: string;
  idToken?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresAt?: string;
  scopes?: string[];
}

export interface OidcUserInfo {
  sub?: string;
  name?: string;
  familyName?: string;
  givenName?: string;
  email?: string;
  emailVerified?: boolean;
  preferredUsername?: string;
}

export interface SsfStreamConfig {
  streamId?: string;
  issuer?: string;
  audience?: string[];
  deliveryMethod?: string;
  endpointUrl?: string;
  eventsRequested?: string[];
  eventsDelivered?: string[];
  status?: string;
  rawConfig?: Record<string, unknown>;
}

export interface OidcConfigData {
  code?: number;
  clientId?: string;
  clientSecret?: string;
  openidConfigurationUrl?: string;
  ssfConfigurationUrl?: string;
  callbackUrl?: string;
  enabled?: boolean;
  configSource?: string;
}

export interface SsfEvent {
  jti?: string;
  iss?: string;
  iat?: string;
  aud?: string;
  eventType?: string;
  subject?: string;
  rawPayload?: Record<string, unknown>;
  receivedAt?: string;
}
