package org.guohai.javasqlweb.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.controller.BackstageController;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.DashboardDao;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.service.operation.DbOperationFactory;
import org.guohai.javasqlweb.util.AccessTokenUtils;
import org.guohai.javasqlweb.util.DbServerTypeUtils;
import org.guohai.javasqlweb.util.DashboardRangeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台服务
 * @author guohai
 * @date 2021-1-1
 */
@Service
public class BackstageServiceImpl implements BackstageService{

    private static final String LEGACY_TLS_MODE = "LEGACY_TLS";
    private static final String DB_SESSION_ID_COLUMN = "db_session_id";
    private static final String QUERY_LOG_DIRECTION_NEWER = "newer";
    private static final String QUERY_LOG_DIRECTION_OLDER = "older";
    private static final int QUERY_LOG_DEFAULT_PAGE_SIZE = 25;
    private static final int QUERY_LOG_MAX_PAGE_SIZE = 100;
    @Autowired
    BaseConfigDao baseConfigDao;

    @Autowired
    UserManageDao userDao;

    @Autowired
    DashboardDao dashboardDao;

    @Autowired
    DataSource dataSource;

    @Autowired
    HealthEndpoint healthEndpoint;

    @Autowired
    UserSecurityTaskService userSecurityTaskService;

    @Autowired
    BaseDataService baseDataService;

    @org.springframework.beans.factory.annotation.Value("${project.legacy-tls-enabled:false}")
    private boolean legacyTlsEnabled;

    private static final Logger LOG  = LoggerFactory.getLogger(BackstageServiceImpl.class);
    /**
     * 获取查询日志
     *
     * @return
     */
    @Override
    public Result<QueryLogCursorResponse> getQueryLog(Integer pageSize, Integer cursorCode, String direction) {
        int normalizedPageSize = normalizeQueryLogPageSize(pageSize);
        String normalizedDirection = normalizeQueryLogDirection(direction);
        List<QueryLogBean> windowRows = QUERY_LOG_DIRECTION_NEWER.equals(normalizedDirection)
                ? baseConfigDao.getQueryLogWindowNewer(cursorCode, normalizedPageSize + 1)
                : baseConfigDao.getQueryLogWindowOlder(cursorCode, normalizedPageSize + 1);

        boolean hasBoundaryRows = windowRows.size() > normalizedPageSize;
        List<QueryLogBean> pageItems = new ArrayList<>(
                hasBoundaryRows ? windowRows.subList(0, normalizedPageSize) : windowRows
        );

        if (QUERY_LOG_DIRECTION_NEWER.equals(normalizedDirection)) {
            pageItems.sort(Comparator.comparing(QueryLogBean::getCode).reversed());
        }

        fillQueryLogTargetTables(pageItems);

        QueryLogCursorResponse response = new QueryLogCursorResponse();
        response.setItems(pageItems);
        response.setPageSize(normalizedPageSize);
        response.setFirstCode(pageItems.isEmpty() ? null : pageItems.get(0).getCode());
        response.setLastCode(pageItems.isEmpty() ? null : pageItems.get(pageItems.size() - 1).getCode());

        if (QUERY_LOG_DIRECTION_NEWER.equals(normalizedDirection)) {
            response.setHasNewer(hasBoundaryRows);
            response.setHasOlder(hasExistingOlderQueryLog(response.getLastCode()));
        } else {
            response.setHasOlder(hasBoundaryRows);
            response.setHasNewer(cursorCode != null && hasExistingNewerQueryLog(response.getFirstCode() == null ? cursorCode : response.getFirstCode()));
        }

        return new Result<>(true, "", response);
    }

    /**
     * 获取连接表
     *
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getConnData() {
        List<ConnectConfigBean> listConn = DbServerTypeUtils.normalize(baseConfigDao.getConnData());

        return new Result<>(true, "", listConn);
    }

    @Override
    public Result<List<PoolStatBean>> getPoolStats() {
        HikariDataSource hikariDataSource = unwrapHikariDataSource();
        if (hikariDataSource == null) {
            return new Result<>(false, "当前数据源不是 HikariDataSource", null);
        }

        PoolStatBean bean = new PoolStatBean();
        bean.setPoolName(hikariDataSource.getPoolName());
        bean.setJdbcUrl(hikariDataSource.getJdbcUrl());
        bean.setDriverClassName(hikariDataSource.getDriverClassName());
        bean.setHealthStatus(healthEndpoint.health().getStatus().getCode());

        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        if (poolMxBean != null) {
            bean.setActiveConnections(poolMxBean.getActiveConnections());
            bean.setIdleConnections(poolMxBean.getIdleConnections());
            bean.setTotalConnections(poolMxBean.getTotalConnections());
            bean.setThreadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection());
        }
        return new Result<>(true, "", Collections.singletonList(bean));
    }

    @Override
    public Result<List<TargetPoolStatBean>> getTargetPoolStats() {
        return baseDataService.getTargetPoolStats();
    }

    @Override
    public Result<List<TargetSessionStatBean>> getTargetPoolSessions(Integer code) {
        if (code == null || baseConfigDao.getConnectConfig(code) == null) {
            return new Result<>(false, "无此服务器", null);
        }
        Result<List<TargetSessionStatBean>> sessionResult = baseDataService.getTargetPoolSessions(code);
        if (!sessionResult.getStatus() || sessionResult.getData() == null) {
            return sessionResult;
        }
        List<TargetSessionStatBean> sessions = new ArrayList<>(sessionResult.getData());
        enrichPlatformUserNames(code, sessions);
        return new Result<>(true, "", sessions);
    }

    /**
     * 测试数据库连接性
     *
     * @param server 服务器信息
     * @return
     */
    @Override
    public Result<String> testServerConnect(ConnectConfigBean server) {
        DbServerTypeUtils.normalize(server);
        if ("mssql".equalsIgnoreCase(server.getDbServerType())
                && LEGACY_TLS_MODE.equalsIgnoreCase(server.getDbSslMode())
                && !legacyTlsEnabled) {
            return new Result<>(false,
                    "当前服务器配置为 LEGACY_TLS，但应用未启用 project.legacy-tls-enabled。请先在部署层显式开启遗留 TLS 模式。",
                    "当前服务器配置为 LEGACY_TLS，但应用未启用 project.legacy-tls-enabled。请先在部署层显式开启遗留 TLS 模式。");
        }
        DbOperation dbOperation = null;
        try {
            dbOperation = createTemporaryDbOperation(server);
            dbOperation.serverHealth();
        } catch (Exception e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
            if ("mssql".equalsIgnoreCase(server.getDbServerType())
                    && e.getMessage() != null
                    && e.getMessage().contains("TLS10 is not accepted by client preferences")) {
                String message;
                if (LEGACY_TLS_MODE.equalsIgnoreCase(server.getDbSslMode())) {
                    message = "目标 SQL Server 仍在要求 TLS 1.0。请检查部署是否已为 JVM 显式开启 TLS1.0 兼容参数，或确认 SQL Server 的 Force Encryption / 证书配置。";
                } else {
                    message = "目标 SQL Server 仅支持 TLS 1.0。若该库位于可信内网且必须兼容，可将连接安全改为 LEGACY_TLS；若服务器启用了 Force Encryption，则需由 DBA 关闭强制加密或升级 TLS。";
                }
                return new Result<>(false, message, message);
            }
            return new Result<>(false,e.getMessage(),e.getMessage());
        } finally {
            closeOperationQuietly(server.getCode(), dbOperation);
        }
        return new Result<>(true,"连接成功","");
    }

    @Override
    public Result<String> testSavedServerConnect(Integer code) {
        ConnectConfigBean savedServer = DbServerTypeUtils.normalize(baseConfigDao.getConnectConfig(code));
        if (savedServer == null) {
            return new Result<>(false, "服务器不存在", "服务器不存在");
        }
        return testServerConnect(savedServer);
    }

    /**
     * 增加服务器
     *
     * @param server
     * @return
     */
    @Override
    public Result<String> addConnServer(ConnectConfigBean server) {
        DbServerTypeUtils.normalize(server);
        if(null != baseConfigDao.getConnectConfigByName(server.getDbServerName())){
            return new Result<>(false,"","同名服务器已经存在");
        }
        baseConfigDao.addConnServer(server);
        if (server.getCode() != null) {
            baseDataService.invalidateServerResources(server.getCode());
        }
        return new Result<>(true, "","服务器增加成功");
    }

    /**
     * 获取站点基础数据
     *
     * @return
     */
    @Override
    public Result<Map> getSiteBaseData() {
        Map<String,Object> baseData = new HashMap<String, Object>(2);
        baseData.put("user_count", userDao.getUserList().size());
        baseData.put("server_count", baseConfigDao.getAllConnectConfig().size());
        return new Result<>(true, "", baseData);
    }

    @Override
    public Result<DashboardResponse> getDashboard(String range,
                                                  String grain,
                                                  Integer userLimit,
                                                  Integer dbLimit,
                                                  Integer tableLimit,
                                                  Integer recentLimit) {
        DashboardRangeUtils.DashboardRange resolvedRange = DashboardRangeUtils.resolve(range, grain);
        DashboardResponse response = new DashboardResponse();
        response.setRange(resolvedRange.getRange());
        response.setGrain(resolvedRange.getGrain());

        PoolStatBean pool = buildPoolSummary();
        response.setPool(pool);
        List<TargetPoolStatBean> dynamicTargetPools = resolveDynamicTargetPools();
        response.setDynamicTargetPools(dynamicTargetPools);

        DashboardSummary summary = buildDashboardSummary(
                resolvedRange.getStartTime(),
                resolvedRange.getEndTime(),
                pool,
                dynamicTargetPools
        );
        response.setSummary(summary);
        response.setTrend(dashboardDao.getTrend(
                resolvedRange.getStartTime(),
                resolvedRange.getEndTime(),
                buildBucketExpr(resolvedRange.getGrain())
        ));

        List<DashboardUserRankingItem> userRanking = dashboardDao.getUserRanking(
                resolvedRange.getStartTime(),
                resolvedRange.getEndTime(),
                normalizeLimit(userLimit, 10)
        );
        for (int i = 0; i < userRanking.size(); i++) {
            userRanking.get(i).setRank(i + 1);
        }
        response.setUserRanking(userRanking);
        response.setDatabaseHotspots(dashboardDao.getDatabaseHotspots(
                resolvedRange.getStartTime(),
                resolvedRange.getEndTime(),
                normalizeLimit(dbLimit, 5)
        ));
        response.setTableHotspots(dashboardDao.getTableHotspots(
                resolvedRange.getStartTime(),
                resolvedRange.getEndTime(),
                normalizeLimit(tableLimit, 10)
        ));
        response.setRecentQueries(dashboardDao.getRecentQueries(
                resolvedRange.getStartTime(),
                resolvedRange.getEndTime(),
                normalizeLimit(recentLimit, 10)
        ));
        return new Result<>(true, "", response);
    }

    /**
     * 获取完整用户数据
     *
     * @return
     */
    @Override
    public Result<List<UserBean>> getUserData() {
        List<UserBean> userList = userDao.getUserListWithAccessToken();
        userList.forEach(this::enrichUserAccessTokenStatus);
        return new Result<>(true, "", userList);
    }

    /**
     * 增加新用户
     *
     * @param user 用户
     * @return
     */
    @Override
    public Result<LinkIssueResult> addNewUser(String token, UserBean user) {
        return userSecurityTaskService.createActivationTask(token, user.getEmail());
    }

    /**
     * 删除指定用户
     *
     * @param userName
     * @return
     */
    @Override
    public Result<String> delUser(String userName) {
        if(null == userDao.getUserByName(userName)){
            return new Result<>(false, "","用户不存在" ) ;
        }
        userDao.delUser(userName);
        return new Result<>(true, "","删除成功");
    }

    /**
     * 删除指定服务器连接
     *
     * @param code
     * @return
     */
    @Override
    public Result<String> delServer(Integer code) {
        if(null == baseConfigDao.getConnectConfig(code)){
            return new Result<>(false, "","无此服务器" ) ;
        }
        baseConfigDao.delServerByCode(code);
        baseDataService.invalidateServerResources(code);
        return new Result<>(true, "","删除成功");
    }

    @Override
    public Result<String> resetServer(Integer code) {
        if (code == null || baseConfigDao.getConnectConfig(code) == null) {
            return new Result<>(false, "", "无此服务器");
        }
        baseDataService.invalidateServerResources(code);
        return new Result<>(true, "", "已重置目标库连接池并清除冷却状态");
    }

    /**
     * 通过有效token修改用户密码
     *
     * @param token
     * @param newPass
     * @return
     */
    @Override
    public Result<LinkIssueResult> reissueActivationLink(String token, String userName) {
        return userSecurityTaskService.reissueActivationTask(token, userName);
    }

    /**
     * 管理员为用户发起密码重置
     * @param token 管理员登录态
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public Result<LinkIssueResult> resetUserPassword(String token, String userName) {
        return userSecurityTaskService.createPasswordResetTask(token, userName);
    }

    /**
     * 管理员为用户发起OTP重绑
     * @param token 管理员登录态
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public Result<LinkIssueResult> resetUserOtp(String token, String userName) {
        return userSecurityTaskService.createOtpResetTask(token, userName);
    }

    /**
     * 更新服务器数据
     *
     * @param server
     * @return
     */
    @Override
    public Result<String> updateServerData(ConnectConfigBean server) {
        DbServerTypeUtils.normalize(server);
        if(null == baseConfigDao.getConnectConfigByCode(server.getCode())){
            return new Result<>(false, "","服务器不存在" );
        }
        baseConfigDao.updateConnServer(server);
        baseDataService.invalidateServerResources(server.getCode());
        return new Result<>(true, "","修改成功");
    }

    private void enrichUserAccessTokenStatus(UserBean user) {
        user.setHasAccessToken(AccessTokenUtils.hasAccessToken(user));
        user.setAccessTokenStatus(AccessTokenUtils.resolveStatus(user));
        user.setAccessToken(null);
        user.setMaskedAccessToken(null);
        user.setAccessTokenFullVisible(false);
        user.setCanCreateAccessToken(null);
        user.setCanRenewAccessToken(null);
        user.setCanResetAccessToken(null);
    }

    private HikariDataSource unwrapHikariDataSource() {
        if (dataSource instanceof HikariDataSource) {
            return (HikariDataSource) dataSource;
        }
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (SQLException e) {
            LOG.warn("Unable to unwrap HikariDataSource", e);
            return null;
        }
    }

    private PoolStatBean buildPoolSummary() {
        Result<List<PoolStatBean>> poolStatsResult = getPoolStats();
        if (!poolStatsResult.getStatus() || poolStatsResult.getData() == null || poolStatsResult.getData().isEmpty()) {
            return new PoolStatBean();
        }
        return poolStatsResult.getData().get(0);
    }

    private DashboardSummary buildDashboardSummary(java.util.Date startTime,
                                                   java.util.Date endTime,
                                                   PoolStatBean pool,
                                                   List<TargetPoolStatBean> dynamicTargetPools) {
        DashboardSummary summary = dashboardDao.getUserSummary(startTime, endTime);
        if (summary == null) {
            summary = new DashboardSummary();
        }
        List<ConnectConfigBean> connectConfigs = baseConfigDao.getConnData();
        summary.setTotalInstances(connectConfigs.size());
        summary.setActivePoolConnections(defaultInteger(pool.getActiveConnections()));
        summary.setIdlePoolConnections(defaultInteger(pool.getIdleConnections()));
        summary.setTotalPoolConnections(defaultInteger(pool.getTotalConnections()));
        summary.setWaitingPoolThreads(defaultInteger(pool.getThreadsAwaitingConnection()));
        summary.setActiveDynamicPools(countActiveDynamicPools(dynamicTargetPools));
        summary.setCooldownDynamicPools(countCooldownDynamicPools(dynamicTargetPools));
        summary.setDynamicPoolConnections(sumDynamicPoolConnections(dynamicTargetPools));
        summary.setDynamicPoolWaitingThreads(sumDynamicPoolWaitingThreads(dynamicTargetPools));
        summary.setQueryCount(defaultLong(dashboardDao.countQueries(startTime, endTime)));
        summary.setTotalReturnedRows(defaultLong(dashboardDao.sumResultRows(startTime, endTime)));
        summary.setAverageQueryConsuming(defaultDouble(dashboardDao.avgQueryConsuming(startTime, endTime)));
        summary.setTotalUsers(defaultInteger(summary.getTotalUsers()));
        summary.setNewUsers(defaultInteger(summary.getNewUsers()));
        return summary;
    }

    private String buildBucketExpr(String grain) {
        if ("day".equalsIgnoreCase(grain)) {
            return "DATE_FORMAT(query_time, '%Y-%m-%d')";
        }
        return "DATE_FORMAT(query_time, '%Y-%m-%d %H:00')";
    }

    private List<TargetPoolStatBean> resolveDynamicTargetPools() {
        Result<List<TargetPoolStatBean>> runtimeResult = baseDataService.getTargetPoolStats();
        if (!runtimeResult.getStatus() || runtimeResult.getData() == null) {
            return Collections.emptyList();
        }
        List<TargetPoolStatBean> filtered = new ArrayList<>();
        for (TargetPoolStatBean stat : runtimeResult.getData()) {
            if (stat == null || "unused".equals(stat.getRuntimeStatus())) {
                continue;
            }
            filtered.add(stat);
        }
        filtered.sort(Comparator
                .comparing((TargetPoolStatBean stat) -> !Boolean.TRUE.equals(stat.getInCooldown()))
                .thenComparing((TargetPoolStatBean stat) -> defaultInteger(stat.getThreadsAwaitingConnection()), Comparator.reverseOrder())
                .thenComparing((TargetPoolStatBean stat) -> defaultInteger(stat.getActiveConnections()), Comparator.reverseOrder())
                .thenComparing((TargetPoolStatBean stat) -> defaultInteger(stat.getServerCode())));
        return filtered;
    }

    private Integer countActiveDynamicPools(List<TargetPoolStatBean> stats) {
        int count = 0;
        for (TargetPoolStatBean stat : stats) {
            if (stat != null && !"unused".equals(stat.getRuntimeStatus())) {
                count++;
            }
        }
        return count;
    }

    private Integer countCooldownDynamicPools(List<TargetPoolStatBean> stats) {
        int count = 0;
        for (TargetPoolStatBean stat : stats) {
            if (stat != null && Boolean.TRUE.equals(stat.getInCooldown())) {
                count++;
            }
        }
        return count;
    }

    private Integer sumDynamicPoolConnections(List<TargetPoolStatBean> stats) {
        int total = 0;
        for (TargetPoolStatBean stat : stats) {
            total += defaultInteger(stat == null ? null : stat.getTotalConnections());
        }
        return total;
    }

    private Integer sumDynamicPoolWaitingThreads(List<TargetPoolStatBean> stats) {
        int total = 0;
        for (TargetPoolStatBean stat : stats) {
            total += defaultInteger(stat == null ? null : stat.getThreadsAwaitingConnection());
        }
        return total;
    }

    private void enrichPlatformUserNames(Integer serverCode, List<TargetSessionStatBean> sessions) {
        List<String> sessionIds = new ArrayList<>();
        for (TargetSessionStatBean session : sessions) {
            if (session != null && session.getSessionId() != null && !session.getSessionId().isBlank()) {
                sessionIds.add(session.getSessionId());
            }
        }
        if (sessionIds.isEmpty()) {
            return;
        }
        List<QueryLogBean> queryLogs;
        try {
            queryLogs = baseConfigDao.getQueryLogsByServerAndSessionIds(serverCode, sessionIds);
        } catch (Exception exception) {
            if (isMissingColumn(exception, DB_SESSION_ID_COLUMN)) {
                LOG.warn("db_query_log is missing db_session_id, skip platform user enrichment for server {}", serverCode);
                return;
            }
            throw exception;
        }
        Map<String, QueryLogBean> latestInFlightBySession = new LinkedHashMap<>();
        for (QueryLogBean queryLog : queryLogs) {
            if (queryLog == null || queryLog.getDbSessionId() == null || queryLog.getDbSessionId().isBlank()) {
                continue;
            }
            if (queryLog.getQueryConsuming() != null) {
                continue;
            }
            latestInFlightBySession.putIfAbsent(queryLog.getDbSessionId(), queryLog);
        }
        for (TargetSessionStatBean session : sessions) {
            QueryLogBean queryLog = session == null ? null : latestInFlightBySession.get(session.getSessionId());
            if (queryLog == null) {
                session.setPlatformUserName(null);
                session.setQueryLogCode(null);
                session.setMatchedByPlatformTrace(false);
                continue;
            }
            session.setPlatformUserName(queryLog.getQueryName());
            session.setQueryLogCode(queryLog.getCode());
            session.setMatchedByPlatformTrace(true);
        }
    }

    private boolean isMissingColumn(Throwable throwable, String columnName) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("unknown column")
                        && normalized.contains(columnName.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private Integer normalizeLimit(Integer value, int defaultValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return value;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private int normalizeQueryLogPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return QUERY_LOG_DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, QUERY_LOG_MAX_PAGE_SIZE);
    }

    private String normalizeQueryLogDirection(String direction) {
        return QUERY_LOG_DIRECTION_NEWER.equalsIgnoreCase(direction)
                ? QUERY_LOG_DIRECTION_NEWER
                : QUERY_LOG_DIRECTION_OLDER;
    }

    private void fillQueryLogTargetTables(List<QueryLogBean> queryLogs) {
        if (queryLogs.isEmpty()) {
            return;
        }

        List<Integer> queryLogCodes = queryLogs.stream()
                .map(QueryLogBean::getCode)
                .filter(code -> code != null)
                .toList();

        if (queryLogCodes.isEmpty()) {
            queryLogs.forEach(queryLog -> queryLog.setTargetTables("-"));
            return;
        }

        Map<Integer, String> targetSummaryMap = new LinkedHashMap<>();
        for (Map<String, Object> row : baseConfigDao.getQueryLogTargetSummaries(queryLogCodes)) {
            Integer queryLogCode = parseInteger(row.get("query_log_code"));
            if (queryLogCode == null) {
                continue;
            }
            targetSummaryMap.put(queryLogCode, String.valueOf(row.get("target_tables")));
        }

        for (QueryLogBean queryLog : queryLogs) {
            queryLog.setTargetTables(targetSummaryMap.getOrDefault(queryLog.getCode(), "-"));
        }
    }

    private boolean hasExistingOlderQueryLog(Integer code) {
        return code != null && Boolean.TRUE.equals(baseConfigDao.existsOlderQueryLog(code));
    }

    private boolean hasExistingNewerQueryLog(Integer code) {
        return code != null && Boolean.TRUE.equals(baseConfigDao.existsNewerQueryLog(code));
    }

    DbOperation createTemporaryDbOperation(ConnectConfigBean server) throws Exception {
        return DbOperationFactory.createDbOperation(server);
    }

    private void closeOperationQuietly(Integer serverCode, DbOperation operation) {
        if (operation == null) {
            return;
        }
        try {
            operation.close();
        } catch (Exception e) {
            LOG.warn("Failed to close temporary operation for server {}", serverCode, e);
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
