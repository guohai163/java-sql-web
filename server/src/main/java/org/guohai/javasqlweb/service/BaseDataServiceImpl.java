package org.guohai.javasqlweb.service;

import jakarta.annotation.PreDestroy;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.QueryLogTargetDao;
import org.guohai.javasqlweb.service.dashboard.WorkbenchDashboardProvider;
import org.guohai.javasqlweb.service.dashboard.WorkbenchDashboardProviderFactory;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.service.operation.DbOperationFactory;
import org.guohai.javasqlweb.util.AuditSqlMaskingUtils;
import org.guohai.javasqlweb.util.DbServerTypeUtils;
import org.guohai.javasqlweb.util.ReadOnlySqlGuard;
import org.guohai.javasqlweb.util.SqlTargetExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础数据操作服务类
 * @author guohai
 * @date 2020-12-1
 */
@Service
public class BaseDataServiceImpl implements BaseDataService{

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(BaseDataServiceImpl.class);
    @Autowired
    BaseConfigDao baseConfigDao;

    @Autowired
    QueryLogTargetDao queryLogTargetDao;

    @Value("${project.limit}")
    private Integer limit;
    /**
     * 服务器实例集合
     */
    private static final Map<Integer, DbOperation> operationMap = new ConcurrentHashMap<>();

    /**
     * 目标库失败状态
     */
    private static final Map<Integer, Integer> connectionFailureCountMap = new ConcurrentHashMap<>();

    private static final Map<Integer, Long> connectionCooldownUntilMap = new ConcurrentHashMap<>();

    private static final Map<Integer, String> connectionLastErrorMap = new ConcurrentHashMap<>();

    private static final Map<Integer, WorkbenchDashboardCacheEntry> workbenchServerDashboardCache = new ConcurrentHashMap<>();

    private static final Map<String, WorkbenchDashboardCacheEntry> workbenchDatabaseDashboardCache = new ConcurrentHashMap<>();

    /**
     * sql最大保存长度
     */
    private static final Integer SAVE_SQL_LENGTH_LIMIT = 8000;

    private static final int CONNECTION_FAILURE_THRESHOLD = 3;

    private static final long CONNECTION_FAILURE_COOLDOWN_MILLIS = 5 * 60 * 1000L;

    private static final long WORKBENCH_DASHBOARD_CACHE_MILLIS = 10 * 60 * 1000L;

    private static class WorkbenchDashboardCacheEntry {
        private final List<WorkbenchDashboardSection> sections;
        private final long cachedAt;
        private final long expiresAt;

        private WorkbenchDashboardCacheEntry(List<WorkbenchDashboardSection> sections, long cachedAt, long expiresAt) {
            this.sections = sections;
            this.cachedAt = cachedAt;
            this.expiresAt = expiresAt;
        }
    }

    @FunctionalInterface
    private interface OperationAction<T> {
        T execute(DbOperation operation) throws Exception;
    }

    @Override
    public Result<List<ConnectConfigBean>> getAllDataConnect() {
        return new Result<>(true,"", DbServerTypeUtils.normalize(baseConfigDao.getAllConnectConfig()));
    }

    /**
     * 获取指定服务器信息
     * @return
     */
    @Override
    public Result<ConnectConfigBean> getServerInfo(Integer serverCode, UserBean user) {
        Result<ConnectConfigBean> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        ConnectConfigBean connBean = DbServerTypeUtils.normalize(baseConfigDao.getConnectConfig(serverCode));
        connBean.setDbServerPassword("");
        connBean.setDbServerUsername("");
        connBean.setDbServerHost("");
        return new Result<>(true,"成功", connBean);
    }

    /**
     * 活的指定DB服务器的库名列表
     *
     * @param serverCode
     * @return
     */
    @Override
    public Result<List<DatabaseNameBean>> getDbName(Integer serverCode, UserBean user) {
        Result<List<DatabaseNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, DbOperation::getDbList);
    }

    /**
     * 获得指定库的所有表名
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<TablesNameBean>> getTableList(Integer serverCode, String dbName, UserBean user) {
        Result<List<TablesNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getTableList(dbName));
    }



    /**
     * 获取所有列名
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public Result<List<ColumnsNameBean>> getColumnList(Integer serverCode, String dbName, String tableName, UserBean user) {
        Result<List<ColumnsNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getColumnsList(dbName, tableName));
    }

    /**
     * 或者指定库的 表列集合
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<Map<String, String[]>> getTableColumn(Integer serverCode, String dbName, UserBean user) {
        Result<Map<String, String[]>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getTablesColumnsMap(dbName));
    }

    /**
     * 获得指定表的所有索引
     *
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public Result<List<TableIndexesBean>> getTableIndexes(Integer serverCode, String dbName, String tableName, UserBean user) {
        Result<List<TableIndexesBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getIndexesList(dbName, tableName));
    }

    /**
     * 获取指定库的视图列表
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<ViewNameBean>> getViewList(Integer serverCode, String dbName, UserBean user) {
        Result<List<ViewNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getViewsList(dbName));
    }

    /**
     * 获取指定视图的创建语句
     *
     * @param serverCode
     * @param dbName
     * @param viewName
     * @return
     */
    @Override
    public Result<ViewNameBean> getViewByName(Integer serverCode, String dbName, String viewName, UserBean user) {
        Result<ViewNameBean> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getView(dbName, viewName));
    }

    /**
     * 获取指定库的存储过程列表,只含名字
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<StoredProceduresBean>> getSpList(Integer serverCode, String dbName, UserBean user) {
        Result<List<StoredProceduresBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getStoredProceduresList(dbName));
    }

    /**
     * 通过存储过程名获取存储过程内容
     *
     * @param serverCode
     * @param dbName
     * @param spName
     * @return
     */
    @Override
    public Result<StoredProceduresBean> getSpByName(Integer serverCode, String dbName, String spName, UserBean user) {
        Result<StoredProceduresBean> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        return executeServerOperation(serverCode, operation -> operation.getStoredProcedure(dbName, spName));
    }

    /**
     * 执行查询语句
     *
     * @param serverCode
     * @param dbName
     * @param sql
     * @param userIp
     * @return
     */
    @Override
    public Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql, UserBean user, String userIp) {
        Result<Object> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        ConnectConfigBean connectConfigBean = DbServerTypeUtils.normalize(baseConfigDao.getConnectConfig(serverCode));
        String guardResult = ReadOnlySqlGuard.validate(sql, connectConfigBean == null ? "" : connectConfigBean.getDbServerType());
        if (guardResult != null) {
            return new Result<>(false, guardResult, null);
        }
        Result<Object> cooldownResult = buildCooldownResultIfPresent(serverCode);
        if (cooldownResult != null) {
            return cooldownResult;
        }
        DbOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                String maskedSql = AuditSqlMaskingUtils.mask(sql);
                String saveSql = maskedSql.length() > SAVE_SQL_LENGTH_LIMIT
                        ? maskedSql.substring(0, SAVE_SQL_LENGTH_LIMIT)
                        : maskedSql;
                QueryLogBean queryLog = new QueryLogBean(userIp, user.getUserName(), dbName, serverCode, saveSql, new Date());
                baseConfigDao.saveQueryLog(queryLog);
                LOG.info(maskedSql);
                Long startTime = System.currentTimeMillis();
                Object[] result = operation.queryDatabaseBySql(dbName, sql, limit);
                Integer resultRowCount = resolveResultRowCount(result);
                String returnResult = Integer.parseInt(result[0].toString())>Integer.parseInt(result[1].toString())?
                        String.format("因程序限制只显示%s条数据",result[1].toString()):
                        "";
                Long endTime = System.currentTimeMillis()-startTime;
                baseConfigDao.updateQueryLogMetrics(queryLog.getCode(), endTime.intValue(), resultRowCount);
                saveQueryTargets(queryLog.getCode(), dbName, sql);
                clearConnectionFailureState(serverCode);
                return new Result<>(true, returnResult, result[2]);
            } catch (Exception e) {
                LOG.warn("SQL query failed for server {}", serverCode, e);
                if (isConnectionFailure(e)) {
                    return buildConnectionFailureResult(serverCode, e);
                }
                return new Result<>(false, extractExceptionMessage(e), null);
            }
        }else{
            return new Result<>(false,"没有找到对应的数据库",null);
        }
    }

    @Override
    public Result<WorkbenchDashboardResponse> getWorkbenchDashboard(Integer serverCode,
                                                                   String dbName,
                                                                   UserBean user,
                                                                   boolean forceRefresh) {
        evictExpiredDashboardCaches(System.currentTimeMillis());
        Result<WorkbenchDashboardResponse> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        if (dbName == null || dbName.trim().isEmpty()) {
            return new Result<>(false, "请选择数据库后再查看 dashboard", null);
        }

        ConnectConfigBean connectConfigBean = DbServerTypeUtils.normalize(baseConfigDao.getConnectConfig(serverCode));
        if (connectConfigBean == null) {
            return new Result<>(false, "没有找到对应的数据库", null);
        }

        WorkbenchDashboardProvider provider = WorkbenchDashboardProviderFactory.getProvider(connectConfigBean.getDbServerType());
        if (provider == null) {
            return new Result<>(false, "当前数据库类型暂不支持 dashboard", null);
        }

        if (!forceRefresh) {
            WorkbenchDashboardResponse cachedResponse = buildWorkbenchDashboardFromCache(serverCode, dbName, connectConfigBean);
            if (cachedResponse != null) {
                return new Result<>(true, "", cachedResponse);
            }
        }

        Result<WorkbenchDashboardResponse> cooldownResult = buildCooldownResultIfPresent(serverCode);
        if (cooldownResult != null) {
            return cooldownResult;
        }

        DbOperation operation = createDbOperation(serverCode);
        if (operation == null) {
            return new Result<>(false, "没有找到对应的数据库", null);
        }

        try {
            List<WorkbenchDashboardSection> sections = provider.buildSections(operation, dbName, connectConfigBean);
            WorkbenchDashboardResponse response = createWorkbenchDashboardResponse(serverCode, dbName, connectConfigBean.getDbServerType(), sections);
            cacheWorkbenchDashboard(serverCode, dbName, response);
            clearConnectionFailureState(serverCode);
            return new Result<>(true, "", response);
        } catch (Exception exception) {
            LOG.warn("Workbench dashboard load failed for server {} db {}", serverCode, dbName, exception);
            if (isConnectionFailure(exception)) {
                return buildConnectionFailureResult(serverCode, exception);
            }
            return new Result<>(false, extractExceptionMessage(exception), null);
        }
    }

    @Override
    public void invalidateServerResources(Integer serverCode) {
        if (serverCode == null) {
            return;
        }
        DbOperation operation = operationMap.remove(serverCode);
        closeOperationQuietly(serverCode, operation);
        clearConnectionFailureState(serverCode);
        workbenchServerDashboardCache.remove(serverCode);
        String cacheKeyPrefix = serverCode + "::";
        workbenchDatabaseDashboardCache.forEach((key, value) -> {
            if (key != null && key.startsWith(cacheKeyPrefix)) {
                workbenchDatabaseDashboardCache.remove(key, value);
            }
        });
    }

    /**
     * 检查所有服务器的健康状态
     *
     * @return
     */
    @Override
    public Result<String> serverHealth() {
        StringBuilder returnMessage = new StringBuilder();
        Boolean serverHealth = true;
        for (Map.Entry<Integer, DbOperation> entry : operationMap.entrySet()) {
            Integer serverCode = entry.getKey();
            Result<String> cooldownResult = buildCooldownResultIfPresent(serverCode);
            if (cooldownResult != null) {
                returnMessage.append(String.format("在健康检查中服务器[%d]处于冷却期：%s\n", serverCode, cooldownResult.getMessage()));
                serverHealth = false;
                continue;
            }
            try {
                entry.getValue().serverHealth();
                clearConnectionFailureState(serverCode);
                returnMessage.append(String.format("在健康检查中服务器[%d]正常\n",serverCode));
                LOG.info(String.format("在健康检查中服务器[%d]正常",serverCode));
            } catch (SQLException throwables) {
                if (isConnectionFailure(throwables)) {
                    Result<String> failureResult = buildConnectionFailureResult(serverCode, throwables);
                    returnMessage.append(String.format("在健康检查中服务器[%d]发生连接异常：%s\n", serverCode, failureResult.getMessage()));
                } else {
                    returnMessage.append(String.format("在健康检查中服务器[%d]发生异常%s\n",serverCode,throwables));
                }
                LOG.error(String.format("在健康检查中服务器[%d]发生异常%s",serverCode,throwables));
                serverHealth = false;
            }
        }
        return new Result<String>(serverHealth,"",returnMessage.toString());
    }

    /**
     * 获取数据库分组
     *
     * @return
     */
    @Override
    public Result<List<String>> getDbGroup(UserBean user) {
        if(null == user){
            return new Result<>(false,"",null);
        }
        return new Result<>(true, "", baseConfigDao.getDbGroup(user.getCode()));
    }

    /**
     * 获取指定用户可以看的列表
     *
     * @param token
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getHavaPermConn(UserBean user) {
        if(null == user){
            return new Result<>(false,"",null);
        }
        return new Result<>(true, "", DbServerTypeUtils.normalize(baseConfigDao.getHavePermConnConfig(user.getCode())));
    }

    /**
     * 获取完整的向导
     *
     * @return
     */
    @Override
    public Result<List<SqlGuidBean>> getAllGuid() {
        return new Result<>(true,"", baseConfigDao.getSqlGuidAll());
    }

    /**
     * 使用单例模式创建一个数据操作实例对象
     * @param serverCode
     * @return
     */
    private DbOperation createDbOperation(Integer serverCode){
        DbOperation dbOperation = operationMap.get(serverCode);
        if(null == dbOperation){
            synchronized (BaseDataServiceImpl.class) {
                if(null == operationMap.get(serverCode)){
                    ConnectConfigBean connConfigBean = baseConfigDao.getConnectConfig(serverCode);
                    try{
                        DbServerTypeUtils.normalize(connConfigBean);
                        dbOperation = DbOperationFactory.createDbOperation(connConfigBean);
                        operationMap.put(serverCode,dbOperation);
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                }
            }
        }
        return dbOperation;
    }

    @PreDestroy
    void closeCachedOperations() {
        for (Map.Entry<Integer, DbOperation> entry : operationMap.entrySet()) {
            closeOperationQuietly(entry.getKey(), entry.getValue());
        }
        operationMap.clear();
        connectionFailureCountMap.clear();
        connectionCooldownUntilMap.clear();
        connectionLastErrorMap.clear();
        workbenchServerDashboardCache.clear();
        workbenchDatabaseDashboardCache.clear();
    }

    private <T> Result<T> validateServerPermission(Integer serverCode, UserBean user) {
        if (user == null || user.getCode() == null) {
            return new Result<>(false, "未认证用户", null);
        }
        if (!Boolean.TRUE.equals(baseConfigDao.hasServerPermission(user.getCode(), serverCode))) {
            return new Result<>(false, "无权限访问该数据库服务器", null);
        }
        return null;
    }

    private Integer resolveResultRowCount(Object[] result) {
        if (result == null || result.length < 2) {
            return 0;
        }
        Integer totalRows = parseInteger(result[0]);
        Integer displayedRows = parseInteger(result[1]);
        return Math.max(totalRows, displayedRows);
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveQueryTargets(Integer queryLogCode, String dbName, String sql) {
        List<QueryLogTargetBean> targets = new ArrayList<>(SqlTargetExtractor.extract(sql, dbName));
        for (QueryLogTargetBean target : targets) {
            target.setQueryLogCode(queryLogCode);
            queryLogTargetDao.saveTarget(target);
        }
    }

    private <T> Result<T> executeServerOperation(Integer serverCode, OperationAction<T> action) {
        Result<T> cooldownResult = buildCooldownResultIfPresent(serverCode);
        if (cooldownResult != null) {
            return cooldownResult;
        }

        DbOperation operation = createDbOperation(serverCode);
        if (operation == null) {
            return new Result<>(false, "没有找到对应的数据库", null);
        }

        try {
            T data = action.execute(operation);
            clearConnectionFailureState(serverCode);
            return new Result<>(true, "", data);
        } catch (Exception e) {
            LOG.warn("Server operation failed for server {}", serverCode, e);
            if (isConnectionFailure(e)) {
                return buildConnectionFailureResult(serverCode, e);
            }
            return new Result<>(false, extractExceptionMessage(e), null);
        }
    }

    private void clearConnectionFailureState(Integer serverCode) {
        connectionFailureCountMap.remove(serverCode);
        connectionCooldownUntilMap.remove(serverCode);
        connectionLastErrorMap.remove(serverCode);
    }

    private WorkbenchDashboardResponse buildWorkbenchDashboardFromCache(Integer serverCode,
                                                                       String dbName,
                                                                       ConnectConfigBean config) {
        WorkbenchDashboardCacheEntry serverEntry = getValidDashboardCacheEntry(workbenchServerDashboardCache, serverCode);
        WorkbenchDashboardCacheEntry databaseEntry = getValidDashboardCacheEntry(
                workbenchDatabaseDashboardCache,
                buildWorkbenchDatabaseCacheKey(serverCode, dbName)
        );
        if (serverEntry == null || databaseEntry == null) {
            return null;
        }
        List<WorkbenchDashboardSection> mergedSections = new ArrayList<>(serverEntry.sections.size() + databaseEntry.sections.size());
        mergedSections.addAll(serverEntry.sections);
        mergedSections.addAll(databaseEntry.sections);
        WorkbenchDashboardResponse response = createWorkbenchDashboardResponse(serverCode, dbName, config.getDbServerType(), mergedSections);
        response.setCachedAt(Math.min(serverEntry.cachedAt, databaseEntry.cachedAt));
        response.setExpiresAt(Math.min(serverEntry.expiresAt, databaseEntry.expiresAt));
        return response;
    }

    private WorkbenchDashboardResponse createWorkbenchDashboardResponse(Integer serverCode,
                                                                       String dbName,
                                                                       String dbType,
                                                                       List<WorkbenchDashboardSection> sections) {
        long now = System.currentTimeMillis();
        WorkbenchDashboardResponse response = new WorkbenchDashboardResponse();
        response.setServerCode(serverCode);
        response.setDbName(dbName);
        response.setDbType(DbServerTypeUtils.normalize(dbType));
        response.setCachedAt(now);
        response.setExpiresAt(now + WORKBENCH_DASHBOARD_CACHE_MILLIS);
        response.setSections(sections);
        return response;
    }

    private void cacheWorkbenchDashboard(Integer serverCode, String dbName, WorkbenchDashboardResponse response) {
        long now = System.currentTimeMillis();
        long expiresAt = now + WORKBENCH_DASHBOARD_CACHE_MILLIS;
        evictExpiredDashboardCaches(now);
        List<WorkbenchDashboardSection> serverSections = new ArrayList<>();
        List<WorkbenchDashboardSection> databaseSections = new ArrayList<>();
        for (WorkbenchDashboardSection section : response.getSections()) {
            if ("system".equals(section.getKey())) {
                serverSections.add(section);
            } else {
                databaseSections.add(section);
            }
        }
        workbenchServerDashboardCache.put(serverCode, new WorkbenchDashboardCacheEntry(serverSections, now, expiresAt));
        workbenchDatabaseDashboardCache.put(
                buildWorkbenchDatabaseCacheKey(serverCode, dbName),
                new WorkbenchDashboardCacheEntry(databaseSections, now, expiresAt)
        );
        response.setCachedAt(now);
        response.setExpiresAt(expiresAt);
    }

    private <K> WorkbenchDashboardCacheEntry getValidDashboardCacheEntry(Map<K, WorkbenchDashboardCacheEntry> cache, K key) {
        WorkbenchDashboardCacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt <= System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    private void evictExpiredDashboardCaches(long now) {
        removeExpiredDashboardEntries(workbenchServerDashboardCache, now);
        removeExpiredDashboardEntries(workbenchDatabaseDashboardCache, now);
    }

    private <K> void removeExpiredDashboardEntries(Map<K, WorkbenchDashboardCacheEntry> cache, long now) {
        cache.forEach((key, entry) -> {
            if (entry != null && entry.expiresAt <= now) {
                cache.remove(key, entry);
            }
        });
    }

    private String buildWorkbenchDatabaseCacheKey(Integer serverCode, String dbName) {
        return serverCode + "::" + dbName;
    }

    private <T> Result<T> buildCooldownResultIfPresent(Integer serverCode) {
        Long cooldownUntilMillis = connectionCooldownUntilMap.get(serverCode);
        if (cooldownUntilMillis == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (cooldownUntilMillis <= now) {
            clearConnectionFailureState(serverCode);
            return null;
        }

        long remainingSeconds = Math.max(1L, (cooldownUntilMillis - now + 999L) / 1000L);
        String lastErrorMessage = connectionLastErrorMap.get(serverCode);
        String suffix = lastErrorMessage == null ? "" : String.format(" 最近错误：%s", lastErrorMessage);
        return new Result<>(false,
                String.format("目标数据库当前处于冷却期，请在约 %d 秒后重试。%s", remainingSeconds, suffix).trim(),
                null);
    }

    private <T> Result<T> buildConnectionFailureResult(Integer serverCode, Exception exception) {
        String errorMessage = extractExceptionMessage(exception);
        long now = System.currentTimeMillis();
        int failureCount = connectionFailureCountMap.merge(serverCode, 1, Integer::sum);
        connectionLastErrorMap.put(serverCode, errorMessage);

        if (failureCount >= CONNECTION_FAILURE_THRESHOLD) {
            connectionCooldownUntilMap.put(serverCode, now + CONNECTION_FAILURE_COOLDOWN_MILLIS);
            return new Result<>(false,
                    String.format("目标数据库连接连续失败 %d 次，已进入冷却期。最近错误：%s",
                            CONNECTION_FAILURE_THRESHOLD,
                            errorMessage),
                    null);
        }

        return new Result<>(false,
                String.format("目标数据库连接失败（%d/%d）：%s",
                        failureCount,
                        CONNECTION_FAILURE_THRESHOLD,
                        errorMessage),
                null);
    }

    private boolean isConnectionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLTransientConnectionException
                    || current instanceof SQLNonTransientConnectionException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof SSLHandshakeException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("08")) {
                    return true;
                }
            }
            String className = current.getClass().getName();
            if (className.contains("CommunicationsException") || className.contains("HttpHostConnectException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("connect failed")
                        || normalizedMessage.contains("connect timed out")
                        || normalizedMessage.contains("connection refused")
                        || normalizedMessage.contains("communications link failure")
                        || normalizedMessage.contains("tcp/ip connection")
                        || normalizedMessage.contains("operation timed out")
                        || normalizedMessage.contains("tls10 is not accepted")
                        || message.contains("尝试连线已失败")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractExceptionMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        if (message != null) {
            return message;
        }
        return throwable == null ? "目标数据库连接失败" : throwable.toString();
    }

    private void closeOperationQuietly(Integer serverCode, DbOperation operation) {
        if (operation == null) {
            return;
        }
        try {
            operation.close();
        } catch (Exception e) {
            LOG.warn("Failed to close cached operation for server {}", serverCode, e);
        }
    }
}
