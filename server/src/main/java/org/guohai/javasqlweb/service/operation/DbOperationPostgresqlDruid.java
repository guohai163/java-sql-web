package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static org.guohai.javasqlweb.util.Utils.closeResource;

public class DbOperationPostgresqlDruid implements DbOperation {
    private static final String PUBLIC_SCHEMA = "public";
    private static final long CACHED_DATASOURCE_IDLE_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_CACHED_DATASOURCES = 16;

    /**
     * 数据源
     */
    private DataSource sqlDs;

    /**
     * 保存每个库的连接
     * 因为postgres数据库的特殊性无法在一个连接里访问多个库，所以 得制造多份连接。
     */
    private final Map<String, CachedDataSource> postgresMap = new HashMap<>();
    /**
     * 保存连接资源
     */
    private ConnectConfigBean connect;

    private final String applicationNamePrefix;

    private static final class CachedDataSource {
        private final DataSource dataSource;
        private long lastAccessAt;
        private int borrowCount;

        private CachedDataSource(DataSource dataSource, long lastAccessAt) {
            this.dataSource = dataSource;
            this.lastAccessAt = lastAccessAt;
        }
    }
    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationPostgresqlDruid(ConnectConfigBean conn) throws Exception {

        connect = conn;
        applicationNamePrefix = "jsw-postgresql-" + conn.getCode();
        sqlDs = createDataSource("postgres");
    }



    /**
     * 获得实例服务器库列表
     *
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    @Override
    public List<DatabaseNameBean> getDbList() throws SQLException, ClassNotFoundException {
        List<DatabaseNameBean> listDnb = new ArrayList<>();
        cleanupCachedDataSources(System.currentTimeMillis(), null);
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT datname FROM pg_database;");
        while (rs.next()){

            String dbName = rs.getString("datname");
            if("template0".equals(dbName) || "template1".equals(dbName)){
                continue;
            }
            listDnb.add(new DatabaseNameBean(dbName));
        }
        closeResource(rs,st,conn);
        return listDnb;
    }

    /**
     * 获得实例指定库的所有表名
     *
     * @param dbName 库名
     * @return 返回集合
     * @throws SQLException 抛出异常
     */
    @Override
    public List<TablesNameBean> getTableList(String dbName) throws SQLException {
        List<TablesNameBean> listTnb = new ArrayList<>();
        DataSource dataSource = acquireDataSource(dbName);
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = dataSource.getConnection();
            st = conn.createStatement();
            rs = st.executeQuery(String.format(
                    "select relname as TABLE_NAME, reltuples as rowCounts from pg_class\n" +
                            "where relkind = 'r' and relnamespace = (select oid from pg_namespace where nspname='public')\n" +
                            "order by TABLE_NAME asc;", dbName));
            while (rs.next()){
                listTnb.add(new TablesNameBean(rs.getString("TABLE_NAME"),
                        rs.getLong("rowCounts")));
            }
        } finally {
            closeResource(rs,st,conn);
            releaseDataSource(dbName);
        }
        return listTnb;
    }

    /**
     * 取回视图列表
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public List<ViewNameBean> getViewsList(String dbName) throws SQLException {
        List<ViewNameBean> viewList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT viewname " +
                            "FROM pg_catalog.pg_views " +
                            "WHERE schemaname = ? " +
                            "ORDER BY viewname"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            rs = ps.executeQuery();
            while (rs.next()) {
                viewList.add(new ViewNameBean(rs.getString("viewname")));
            }
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
        return viewList;
    }

    /**
     * 获取视图详细信息
     *
     * @param dbName
     * @param viewName
     * @return
     * @throws SQLException
     */
    @Override
    public ViewNameBean getView(String dbName, String viewName) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT COALESCE(definition, '') AS view_definition " +
                            "FROM pg_catalog.pg_views " +
                            "WHERE schemaname = ? AND viewname = ?"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, viewName);
            rs = ps.executeQuery();
            if (rs.next()) {
                String definition = rs.getString("view_definition");
                return new ViewNameBean(
                        viewName,
                        String.format("CREATE OR REPLACE VIEW %s.%s AS%n%s",
                                quoteIdentifier(PUBLIC_SCHEMA),
                                quoteIdentifier(viewName),
                                definition == null ? "" : definition)
                );
            }
            return new ViewNameBean(viewName, "");
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
    }

    /**
     * 获取所有列名
     *
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        List<ColumnsNameBean> columnList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT c.column_name, " +
                            "       CASE " +
                            "           WHEN c.data_type = 'USER-DEFINED' THEN c.udt_name " +
                            "           WHEN c.data_type = 'ARRAY' THEN c.udt_name " +
                            "           ELSE c.data_type " +
                            "       END AS column_type, " +
                            "       CASE " +
                            "           WHEN c.character_maximum_length IS NOT NULL THEN c.character_maximum_length::text " +
                            "           WHEN c.numeric_precision IS NOT NULL AND c.numeric_scale IS NOT NULL THEN c.numeric_precision::text || ',' || c.numeric_scale::text " +
                            "           WHEN c.numeric_precision IS NOT NULL THEN c.numeric_precision::text " +
                            "           WHEN c.datetime_precision IS NOT NULL THEN c.datetime_precision::text " +
                            "           ELSE '' " +
                            "       END AS column_length, " +
                            "       COALESCE(pd.description, '') AS column_comment, " +
                            "       CASE WHEN c.is_nullable = 'NO' THEN 'not null' ELSE 'null' END AS column_is_null " +
                            "FROM information_schema.columns c " +
                            "LEFT JOIN pg_catalog.pg_namespace pn " +
                            "       ON pn.nspname = c.table_schema " +
                            "LEFT JOIN pg_catalog.pg_class pc " +
                            "       ON pc.relname = c.table_name AND pc.relnamespace = pn.oid " +
                            "LEFT JOIN pg_catalog.pg_attribute pa " +
                            "       ON pa.attrelid = pc.oid AND pa.attname = c.column_name " +
                            "LEFT JOIN pg_catalog.pg_description pd " +
                            "       ON pd.objoid = pc.oid AND pd.objsubid = pa.attnum " +
                            "WHERE c.table_schema = ? AND c.table_name = ? " +
                            "ORDER BY c.ordinal_position"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, tableName);
            rs = ps.executeQuery();
            while (rs.next()) {
                columnList.add(new ColumnsNameBean(
                        rs.getString("column_name"),
                        rs.getString("column_type"),
                        rs.getString("column_length"),
                        rs.getString("column_comment"),
                        rs.getString("column_is_null")
                ));
            }
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
        return columnList;
    }

    /**
     * 获取所有的索引数据
     *
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public List<TableIndexesBean> getIndexesList(String dbName, String tableName) throws SQLException {
        List<TableIndexesBean> indexList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT ci.relname AS index_name, " +
                            "       pg_get_indexdef(i.indexrelid) AS index_description, " +
                            "       COALESCE(string_agg(a.attname, ', ' ORDER BY k.ordinality), '') AS index_keys " +
                            "FROM pg_catalog.pg_class ct " +
                            "JOIN pg_catalog.pg_namespace nt " +
                            "  ON nt.oid = ct.relnamespace " +
                            "JOIN pg_catalog.pg_index i " +
                            "  ON i.indrelid = ct.oid " +
                            "JOIN pg_catalog.pg_class ci " +
                            "  ON ci.oid = i.indexrelid " +
                            "LEFT JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, ordinality) " +
                            "  ON TRUE " +
                            "LEFT JOIN pg_catalog.pg_attribute a " +
                            "  ON a.attrelid = ct.oid AND a.attnum = k.attnum " +
                            "WHERE nt.nspname = ? " +
                            "  AND ct.relname = ? " +
                            "  AND ct.relkind = 'r' " +
                            "GROUP BY ci.relname, i.indexrelid " +
                            "ORDER BY ci.relname"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, tableName);
            rs = ps.executeQuery();
            while (rs.next()) {
                indexList.add(new TableIndexesBean(
                        rs.getString("index_name"),
                        rs.getString("index_description"),
                        rs.getString("index_keys")
                ));
            }
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
        return indexList;
    }

    /**
     * 获取指定库的所有存储过程列表
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public List<StoredProceduresBean> getStoredProceduresList(String dbName) throws SQLException {
        List<StoredProceduresBean> procedureList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT DISTINCT routine_name " +
                            "FROM information_schema.routines " +
                            "WHERE specific_schema = ? " +
                            "ORDER BY routine_name"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            rs = ps.executeQuery();
            while (rs.next()) {
                procedureList.add(new StoredProceduresBean(rs.getString("routine_name")));
            }
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
        return procedureList;
    }

    /**
     * 获取指定存储过程内容
     *
     * @param dbName
     * @param spName
     * @return
     * @throws SQLException
     */
    @Override
    public StoredProceduresBean getStoredProcedure(String dbName, String spName) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT COALESCE(pg_get_functiondef(p.oid), '') AS routine_definition " +
                            "FROM pg_catalog.pg_proc p " +
                            "JOIN pg_catalog.pg_namespace n " +
                            "  ON n.oid = p.pronamespace " +
                            "WHERE n.nspname = ? " +
                            "  AND p.proname = ? " +
                            "ORDER BY p.oid " +
                            "LIMIT 1"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, spName);
            rs = ps.executeQuery();
            if (rs.next()) {
                return new StoredProceduresBean(spName, rs.getString("routine_definition"));
            }
            return new StoredProceduresBean(spName, "");
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
    }

    /**
     * 执行查询的SQL
     *
     * @param dbName
     * @param sql
     * @param limit
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public Object[] queryDatabaseBySql(String dbName, String sql, Integer limit) throws SQLException {
        return queryDatabaseBySqlWithSession(dbName, sql, limit, null).getRows();
    }

    @Override
    public QueryExecutionResult queryDatabaseBySqlWithSession(String dbName, String sql, Integer limit, java.util.function.Consumer<String> onSessionReady) throws SQLException {
        Object[] result = new Object[3];
        List<Map<String, Object>> listData = new ArrayList<>();
        DataSource dataSource = acquireDataSource(dbName);
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        String sessionId = null;
        try{
            conn = dataSource.getConnection();
            sessionId = queryCurrentSessionId(conn);
            if (onSessionReady != null) {
                onSessionReady.accept(sessionId);
            }
            st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
            rs = st.executeQuery(String.format("%s;", sql));
            // 获得结果集结构信息,元数据
            java.sql.ResultSetMetaData md = rs.getMetaData();
            // 获得列数
            int columnCount = md.getColumnCount();
            rs.last();
            result[0] = rs.getRow();
            rs.beforeFirst();
            int dataCount = 1;
            while (rs.next()){
                if(dataCount>limit){
                    break;
                }
                dataCount++;
                Map<String, Object> rowData = new LinkedHashMap<String, Object>();
                for(int i=1;i<=columnCount;i++){
                    rowData.put(md.getColumnLabel(i),md.getColumnType(i) == 93
                            ? (rs.getObject(i)==null?"NULL":rs.getDate(i) + " " + rs.getTime(i))
                            : rs.getObject(i));
                }
                listData.add(rowData);
            }

            result[1] = listData.size();
            result[2] = listData;
        }
        catch (SQLException e){
            throw e;
        }
        finally {
            closeResource(rs,st,conn);
            releaseDataSource(dbName);
        }

        QueryExecutionResult executionResult = new QueryExecutionResult();
        executionResult.setDbSessionId(sessionId);
        executionResult.setRows(result);
        return executionResult;
    }

    /**
     * 返回一个数据库的所有表和列集合
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public Map<String, String[]> getTablesColumnsMap(String dbName) throws SQLException {
        Map<String, String[]> tables = new HashMap<>(16);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DataSource dataSource = acquireDataSource(dbName);
        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(
                    "SELECT table_name, string_agg(column_name, ',' ORDER BY ordinal_position) AS column_names " +
                            "FROM information_schema.columns " +
                            "WHERE table_schema = ? " +
                            "GROUP BY table_name"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            rs = ps.executeQuery();
            while (rs.next()) {
                String columnNames = rs.getString("column_names");
                tables.put(
                        rs.getString("table_name"),
                        columnNames == null || columnNames.isEmpty() ? new String[0] : columnNames.split(",")
                );
            }
        } finally {
            closeResource(rs, ps, conn);
            releaseDataSource(dbName);
        }
        return tables;
    }

    /**
     * 服务器连接状态健康检查
     *
     * @return
     * @throws SQLException
     */
    @Override
    public Boolean serverHealth() throws SQLException {
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT now();");
        closeResource(rs,st,conn);
        return true;
    }

    @Override
    public void close() {
        HikariDataSourceUtils.closeDataSource(sqlDs);
        synchronized (postgresMap) {
            for (CachedDataSource cachedDataSource : postgresMap.values()) {
                HikariDataSourceUtils.closeDataSource(cachedDataSource.dataSource);
            }
            postgresMap.clear();
        }
    }

    @Override
    public PoolStatBean describeRuntimePool() {
        synchronized (postgresMap) {
            cleanupCachedDataSources(System.currentTimeMillis(), null);
            if (postgresMap.isEmpty()) {
                return null;
            }
            PoolStatBean bean = new PoolStatBean();
            int poolCount = 0;
            String firstPoolName = null;
            for (CachedDataSource cachedDataSource : postgresMap.values()) {
                PoolStatBean current = describeDataSourcePool(cachedDataSource.dataSource);
                if (current == null) {
                    continue;
                }
                poolCount++;
                if (firstPoolName == null && current.getPoolName() != null && !current.getPoolName().isBlank()) {
                    firstPoolName = current.getPoolName();
                }
                if (bean.getJdbcUrl() == null) {
                    bean.setJdbcUrl(current.getJdbcUrl());
                }
                if (bean.getDriverClassName() == null) {
                    bean.setDriverClassName(current.getDriverClassName());
                }
                bean.setActiveConnections(defaultInteger(bean.getActiveConnections()) + defaultInteger(current.getActiveConnections()));
                bean.setIdleConnections(defaultInteger(bean.getIdleConnections()) + defaultInteger(current.getIdleConnections()));
                bean.setTotalConnections(defaultInteger(bean.getTotalConnections()) + defaultInteger(current.getTotalConnections()));
                bean.setThreadsAwaitingConnection(defaultInteger(bean.getThreadsAwaitingConnection()) + defaultInteger(current.getThreadsAwaitingConnection()));
            }
            if (poolCount == 0) {
                return null;
            }
            if (poolCount == 1) {
                bean.setPoolName(firstPoolName);
            } else if (firstPoolName != null && !firstPoolName.isBlank()) {
                bean.setPoolName(firstPoolName + " +" + (poolCount - 1));
            } else {
                bean.setPoolName("jsw-postgresql-pools(" + poolCount + ")");
            }
            return bean;
        }
    }

    @Override
    public List<TargetSessionStatBean> listActiveSessions() throws SQLException {
        List<TargetSessionStatBean> sessions = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            ps = conn.prepareStatement(
                    "SELECT pid::text AS session_id, " +
                            "       usename AS database_user_name, " +
                            "       COALESCE(client_addr::text, client_hostname, '') AS client_host, " +
                            "       datname AS database_name, " +
                            "       state AS session_status, " +
                            "       COALESCE(wait_event_type || ':' || wait_event, state) AS command_or_wait, " +
                            "       CAST(EXTRACT(EPOCH FROM now() - COALESCE(query_start, state_change)) AS bigint) AS running_seconds, " +
                            "       query_start AS query_start_time, " +
                            "       query AS sql_text " +
                            "FROM pg_stat_activity " +
                            "WHERE application_name LIKE ? " +
                            "  AND pid <> pg_backend_pid() " +
                            "  AND state <> 'idle' " +
                            "ORDER BY running_seconds DESC, pid DESC"
            );
            ps.setString(1, applicationNamePrefix + "-%");
            rs = ps.executeQuery();
            while (rs.next()) {
                TargetSessionStatBean bean = new TargetSessionStatBean();
                bean.setDbType("postgresql");
                bean.setSessionId(rs.getString("session_id"));
                bean.setDatabaseUserName(rs.getString("database_user_name"));
                bean.setClientHost(rs.getString("client_host"));
                bean.setDatabaseName(rs.getString("database_name"));
                bean.setSessionStatus(rs.getString("session_status"));
                bean.setCommandOrWait(rs.getString("command_or_wait"));
                bean.setRunningSeconds(parseLong(rs.getObject("running_seconds")));
                bean.setQueryStartTime(rs.getTimestamp("query_start_time"));
                bean.setSqlText(rs.getString("sql_text"));
                sessions.add(bean);
            }
        } finally {
            closeResource(rs, ps, conn);
        }
        return sessions;
    }


    /**
     * 通过库名制造一个连接串
     * @param dbName
     * @return
     */
    private DataSource createDataSource(String dbName){
        return HikariDataSourceUtils.createDataSource(
                applicationNamePrefix + "-" + dbName,
                String.format("jdbc:postgresql://%s:%s/%s?ApplicationName=%s",
                        connect.getDbServerHost(), connect.getDbServerPort(), dbName, applicationNamePrefix + "-" + dbName),
                connect.getDbServerUsername(),
                connect.getDbServerPassword(),
                "select now()"
        );
    }

    private DataSource acquireDataSource(String dbName) {
        synchronized (postgresMap) {
            long now = System.currentTimeMillis();
            cleanupCachedDataSources(now, dbName);
            CachedDataSource cachedDataSource = postgresMap.get(dbName);
            if (cachedDataSource == null) {
                cachedDataSource = new CachedDataSource(createDataSource(dbName), now);
                postgresMap.put(dbName, cachedDataSource);
            }
            cachedDataSource.lastAccessAt = now;
            cachedDataSource.borrowCount++;
            return cachedDataSource.dataSource;
        }
    }

    private void releaseDataSource(String dbName) {
        synchronized (postgresMap) {
            CachedDataSource cachedDataSource = postgresMap.get(dbName);
            if (cachedDataSource == null) {
                return;
            }
            if (cachedDataSource.borrowCount > 0) {
                cachedDataSource.borrowCount--;
            }
            cachedDataSource.lastAccessAt = System.currentTimeMillis();
            cleanupCachedDataSources(cachedDataSource.lastAccessAt, dbName);
        }
    }

    private void cleanupCachedDataSources(long now, String keepDbName) {
        List<String> idleKeys = new ArrayList<>();
        for (Map.Entry<String, CachedDataSource> entry : postgresMap.entrySet()) {
            if (keepDbName != null && keepDbName.equals(entry.getKey())) {
                continue;
            }
            CachedDataSource cachedDataSource = entry.getValue();
            if (cachedDataSource.borrowCount == 0
                    && now - cachedDataSource.lastAccessAt >= CACHED_DATASOURCE_IDLE_MILLIS) {
                idleKeys.add(entry.getKey());
            }
        }
        for (String dbName : idleKeys) {
            closeCachedDataSource(dbName);
        }

        while (postgresMap.size() > MAX_CACHED_DATASOURCES) {
            String oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (Map.Entry<String, CachedDataSource> entry : postgresMap.entrySet()) {
                if (keepDbName != null && keepDbName.equals(entry.getKey())) {
                    continue;
                }
                CachedDataSource cachedDataSource = entry.getValue();
                if (cachedDataSource.borrowCount > 0) {
                    continue;
                }
                if (cachedDataSource.lastAccessAt < oldestAccess) {
                    oldestAccess = cachedDataSource.lastAccessAt;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                break;
            }
            closeCachedDataSource(oldestKey);
        }
    }

    private void closeCachedDataSource(String dbName) {
        CachedDataSource cachedDataSource = postgresMap.remove(dbName);
        if (cachedDataSource != null) {
            HikariDataSourceUtils.closeDataSource(cachedDataSource.dataSource);
        }
    }

    private PoolStatBean describeDataSourcePool(DataSource dataSource) {
        HikariDataSource hikariDataSource = unwrapHikariDataSource(dataSource);
        if (hikariDataSource == null) {
            return null;
        }
        PoolStatBean bean = new PoolStatBean();
        bean.setPoolName(hikariDataSource.getPoolName());
        bean.setJdbcUrl(hikariDataSource.getJdbcUrl());
        bean.setDriverClassName(hikariDataSource.getDriverClassName());
        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        if (poolMxBean != null) {
            bean.setActiveConnections(poolMxBean.getActiveConnections());
            bean.setIdleConnections(poolMxBean.getIdleConnections());
            bean.setTotalConnections(poolMxBean.getTotalConnections());
            bean.setThreadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection());
        }
        return bean;
    }

    private HikariDataSource unwrapHikariDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource;
        }
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String queryCurrentSessionId(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT pg_backend_pid()::text AS value")) {
            if (resultSet.next()) {
                return resultSet.getString("value");
            }
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

}
