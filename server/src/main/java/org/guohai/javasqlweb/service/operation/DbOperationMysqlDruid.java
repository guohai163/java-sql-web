package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;
import org.guohai.javasqlweb.util.ReadOnlySqlGuard;
import org.guohai.javasqlweb.util.SqlIdentifierUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.guohai.javasqlweb.util.Utils.closeResource;

/**
 * Mysql操作实现类
 * @author guohai
 * @date 2021-1-1
 */
public class DbOperationMysqlDruid implements DbOperation {

    private static final DateTimeFormatter MYSQL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MYSQL_DATETIME_MILLIS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String MYSQL_PROGRAM_NAME_PREFIX = "jsw-mysql-";

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(DbOperationMysqlDruid.class);

    /**
     * 数据源
     */
    private DataSource sqlDs;

    private int queryTimeoutSeconds;

    private final String applicationName;

    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationMysqlDruid(ConnectConfigBean conn) throws Exception {
        applicationName = MYSQL_PROGRAM_NAME_PREFIX + conn.getCode();
        sqlDs = HikariDataSourceUtils.createDataSource(
                applicationName,
                String.format("jdbc:mysql://%s:%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true&connectionAttributes=program_name:%s",
                        conn.getDbServerHost(), conn.getDbServerPort(), applicationName),
                conn.getDbServerUsername(),
                conn.getDbServerPassword(),
                "select now()"
        );
    }

    DbOperationMysqlDruid(DataSource dataSource) {
        this.sqlDs = dataSource;
        this.applicationName = null;
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
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SHOW DATABASES;");
        while (rs.next()){
            listDnb.add(new DatabaseNameBean(rs.getString("Database")));
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
        Connection conn = sqlDs.getConnection();
        PreparedStatement st = conn.prepareStatement(
                "SELECT table_name, table_rows, COALESCE(table_comment, '') AS table_comment " +
                        "FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name ASC");
        st.setString(1, dbName);
        ResultSet rs = st.executeQuery();
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getString("table_name"),
                    rs.getLong("table_rows"),
                    rs.getString("table_comment")));
        }
        closeResource(rs,st,conn);
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
        List<ViewNameBean> listView = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        PreparedStatement st = conn.prepareStatement(
                "SELECT table_name, view_definition FROM information_schema.views WHERE table_schema = ? ORDER BY table_name");
        st.setString(1, dbName);
        ResultSet rs = st.executeQuery();
        while (rs.next()){
            listView.add(new ViewNameBean(rs.getString("table_name"), rs.getString("view_definition")));
        }
        closeResource(rs,st,conn);
        return listView;
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
        return null;
    }

    /**
     * 获取指定表的列元数据；目标表不存在时返回空列表。
     *
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 连接或元数据查询失败时抛出异常
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        List<ColumnsNameBean> listCnb = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        PreparedStatement st = conn.prepareStatement(
                "SELECT column_name, column_type, column_comment, is_nullable " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position");
        st.setString(1, dbName);
        st.setString(2, tableName);
        ResultSet rs = st.executeQuery();
        while (rs.next()){
            listCnb.add(new ColumnsNameBean(rs.getString("column_name"),
                    rs.getString("column_type"),
                    "",
                    rs.getString("column_comment"),
                    "NO".equals(rs.getString("is_nullable"))?"not null":"null"));
        }
        closeResource(rs,st,conn);
        return listCnb;
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
        Map<String, String[]> tables = new HashMap<>(10);
        Connection conn = sqlDs.getConnection();
        PreparedStatement st = conn.prepareStatement(
                "SELECT table_name, GROUP_CONCAT(column_name ORDER BY ordinal_position) AS column_name " +
                        "FROM information_schema.columns WHERE table_schema = ? GROUP BY table_name");
        st.setString(1, dbName);
        ResultSet rs = st.executeQuery();
        while (rs.next()){
            String columnNames = rs.getString("column_name");
            tables.put(rs.getString("table_name"), columnNames == null || columnNames.isEmpty() ? new String[0] : columnNames.split(","));
        }
        closeResource(rs,st,conn);
        return tables;
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
        List<TableIndexesBean> listTib = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        PreparedStatement st = conn.prepareStatement(
                "SELECT index_name, COALESCE(index_comment, '') AS index_comment, column_name " +
                        "FROM information_schema.statistics " +
                        "WHERE table_schema = ? AND table_name = ? ORDER BY index_name, seq_in_index");
        st.setString(1, dbName);
        st.setString(2, tableName);
        ResultSet rs = st.executeQuery();
        while (rs.next()){
            listTib.add(new TableIndexesBean(rs.getString("index_name"),
                    rs.getString("index_comment"),
                    rs.getString("column_name")));
        }
        closeResource(rs,st,conn);
        return listTib;
    }

    /**
     * 获取指定库的所有存储过程列表
     *
     * @param dbName 数据库db
     * @return 存储过程名
     * @throws SQLException
     */
    @Override
    public List<StoredProceduresBean> getStoredProceduresList(String dbName) throws SQLException {
        List<StoredProceduresBean> listSp = new ArrayList<>();
        Connection conn = null;
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            st = conn.prepareStatement(
                    "SELECT specific_name FROM information_schema.routines " +
                            "WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY specific_name");
            st.setString(1, dbName);
            rs = st.executeQuery();
            while (rs.next()) {
                listSp.add(new StoredProceduresBean(rs.getString("specific_name")));
            }
        } finally {
            closeResource(rs, st, conn);
        }
        return listSp;
    }

    /**
     * 获取指定存储过程内容
     *
     * @param dbName 数据库db
     * @param spName 存储过程名
     * @return StoredProceduresBean 存储过程内容
     * @throws SQLException
     */
    @Override
    public StoredProceduresBean getStoredProcedure(String dbName, String spName) throws SQLException {
        StoredProceduresBean spBean = null;
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            st = conn.createStatement();
            rs = st.executeQuery("SHOW CREATE PROCEDURE "
                    + SqlIdentifierUtils.quoteMysqlIdentifier(dbName)
                    + "."
                    + SqlIdentifierUtils.quoteMysqlIdentifier(spName));
            while (rs.next()) {
                spBean = new StoredProceduresBean(spName, rs.getString("Create Procedure"));
            }
        } finally {
            closeResource(rs, st, conn);
        }
        return spBean;
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
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        String sessionId = null;
        try{
            conn = sqlDs.getConnection();
            sessionId = queryCurrentSessionId(conn);
            if (onSessionReady != null) {
                onSessionReady.accept(sessionId);
            }
            st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
            QueryExecutionUtils.applyQueryControls(st, queryTimeoutSeconds, limit);
            // 数据库名无法参数化，必须先通过方言标识符转义再切换 catalog。
            st.execute("USE " + SqlIdentifierUtils.quoteMysqlIdentifier(dbName));
            List<String> splitSql = ReadOnlySqlGuard.splitStatements(sql);
            String querySql = null;
            for (String statement : splitSql) {
                String trimmedStatement = statement == null ? "" : statement.trim();
                if (trimmedStatement.isEmpty()) {
                    continue;
                }
                if (querySql != null) {
                    st.execute(QueryExecutionUtils.ensureTrailingSemicolon(querySql));
                }
                querySql = trimmedStatement;
            }
            if (querySql == null) {
                throw new SQLException("SQL query is empty");
            }
            rs = st.executeQuery(QueryExecutionUtils.ensureTrailingSemicolon(querySql));
            // 获得结果集结构信息,元数据
            java.sql.ResultSetMetaData md = rs.getMetaData();
            // 获得列数
            int columnCount = md.getColumnCount();
            boolean hasMore = false;
            while (rs.next()){
                if(QueryExecutionUtils.shouldStopBeforeAdding(listData, limit)){
                    hasMore = true;
                    break;
                }
                Map<String, Object> rowData = new LinkedHashMap<>();
                for(int i=1;i<=columnCount;i++){
                    String columnTypeName = md.getColumnTypeName(i);
                    Object object;
                    // MySQL DATETIME 不带时区语义，避免走 Timestamp 触发 8 小时换算
                    if (isMysqlDateTimeColumn(columnTypeName)) {
                        object = formatMysqlDateTime(rs, i);
                    } else if (md.getColumnType(i) == Types.TIMESTAMP) {
                        object = rawMysqlTemporalValue(rs, i);
                    } else {
                        object = rs.getObject(i);
                    }
                    rowData.put(md.getColumnLabel(i), object);
                }
                listData.add(rowData);
            }

            QueryExecutionUtils.fillResult(result, listData, hasMore);
        } finally {
            closeResource(rs,st,conn);
        }

        QueryExecutionResult executionResult = new QueryExecutionResult();
        executionResult.setDbSessionId(sessionId);
        executionResult.setRows(result);
        return executionResult;
    }

    @Override
    public void configureQueryTimeoutSeconds(int seconds) {
        queryTimeoutSeconds = Math.max(0, seconds);
    }

    @Override
    public PoolStatBean describeRuntimePool() {
        HikariDataSource hikariDataSource = unwrapHikariDataSource();
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

    @Override
    public List<TargetSessionStatBean> listActiveSessions() throws SQLException {
        if (applicationName == null || applicationName.isBlank()) {
            return List.of();
        }
        List<TargetSessionStatBean> sessions = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            ps = conn.prepareStatement(
                    "SELECT CAST(p.ID AS CHAR) AS session_id, " +
                            "       p.USER AS database_user_name, " +
                            "       p.HOST AS client_host, " +
                            "       p.DB AS database_name, " +
                            "       COALESCE(NULLIF(p.STATE, ''), p.COMMAND) AS session_status, " +
                            "       p.COMMAND AS command_or_wait, " +
                            "       CAST(p.TIME AS SIGNED) AS running_seconds, " +
                            "       DATE_SUB(NOW(), INTERVAL p.TIME SECOND) AS query_start_time, " +
                            "       COALESCE(esc.SQL_TEXT, p.INFO) AS sql_text " +
                            "FROM information_schema.PROCESSLIST p " +
                            "JOIN performance_schema.threads t ON t.PROCESSLIST_ID = p.ID " +
                            "JOIN performance_schema.session_connect_attrs a " +
                            "  ON a.PROCESSLIST_ID = p.ID AND a.ATTR_NAME = 'program_name' " +
                            "LEFT JOIN performance_schema.events_statements_current esc ON esc.THREAD_ID = t.THREAD_ID " +
                            "WHERE a.ATTR_VALUE = ? " +
                            "  AND p.ID <> CONNECTION_ID() " +
                            "  AND (p.COMMAND <> 'Sleep' OR COALESCE(esc.SQL_TEXT, p.INFO) IS NOT NULL) " +
                            "ORDER BY p.TIME DESC, p.ID DESC"
            );
            ps.setString(1, applicationName);
            rs = ps.executeQuery();
            while (rs.next()) {
                TargetSessionStatBean bean = new TargetSessionStatBean();
                bean.setDbType("mysql");
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

    private boolean isMysqlDateTimeColumn(String columnTypeName) {
        if (columnTypeName == null) {
            return false;
        }
        String normalizedTypeName = columnTypeName.trim().toLowerCase();
        return normalizedTypeName.contains("datetime");
    }

    private String formatMysqlDateTime(ResultSet rs, int columnIndex) throws SQLException {
        try {
            LocalDateTime localDateTime = rs.getObject(columnIndex, LocalDateTime.class);
            if (localDateTime == null) {
                return "NULL";
            }
            return formatLocalDateTime(localDateTime);
        } catch (SQLException ex) {
            String rawValue = rs.getString(columnIndex);
            if (rawValue == null) {
                return "NULL";
            }
            return rawValue;
        }
    }

    private String rawMysqlTemporalValue(ResultSet rs, int columnIndex) throws SQLException {
        String rawValue = rs.getString(columnIndex);
        return rawValue == null ? "NULL" : rawValue;
    }

    private String formatLocalDateTime(LocalDateTime localDateTime) {
        if (localDateTime.getNano() == 0) {
            return localDateTime.format(MYSQL_DATETIME_FORMATTER);
        }
        return localDateTime.format(MYSQL_DATETIME_MILLIS_FORMATTER);
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
        ResultSet rs = st.executeQuery("SELECT now()");
        closeResource(rs,st,conn);
        return true;
    }

    @Override
    public void close() {
        HikariDataSourceUtils.closeDataSource(sqlDs);
    }

    private HikariDataSource unwrapHikariDataSource() {
        if (sqlDs instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource;
        }
        try {
            return sqlDs.unwrap(HikariDataSource.class);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private String queryCurrentSessionId(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT CONNECTION_ID() AS value")) {
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


}
