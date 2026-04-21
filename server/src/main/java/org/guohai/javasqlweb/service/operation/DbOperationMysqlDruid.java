package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;
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
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SELECT table_name ,table_rows " +
                "FROM `information_schema`.`tables` WHERE TABLE_SCHEMA = '%s' ORDER BY table_name DESC;", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getString("table_name"),
                    rs.getLong("table_rows")));
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
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SELECT TABLE_NAME,VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA='%s'", dbName));
        while (rs.next()){
            listView.add(new ViewNameBean(rs.getString("TABLE_NAME"), rs.getString("VIEW_DEFINITION")));
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
     * 获取所有列名
     *
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        List<ColumnsNameBean> listCnb = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("SHOW FULL COLUMNS FROM %s.%s", dbName, tableName));
        while (rs.next()){
            listCnb.add(new ColumnsNameBean(rs.getString("Field"),
                    rs.getString("Type"),
                    "",
                    rs.getString("Comment"),
                    "NO".equals(rs.getString("Null"))?"not null":"null"));
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
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("select TABLE_NAME,group_concat(COLUMN_NAME) as COLUMN_NAME from `information_schema`.`COLUMNS` where  TABLE_SCHEMA='%s' group by TABLE_NAME;", dbName));
        while (rs.next()){
            tables.put(rs.getString("TABLE_NAME"),rs.getString("COLUMN_NAME").split(","));
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
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SHOW INDEX FROM %s.%s", dbName, tableName));
        while (rs.next()){
            listTib.add(new TableIndexesBean(rs.getObject("Key_name").toString(),
                    rs.getObject("Comment").toString(),
                    rs.getObject("Column_name").toString()));
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
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            st = conn.createStatement();
            rs = st.executeQuery(String.format(
                    "SELECT SPECIFIC_NAME FROM information_schema.Routines WHERE ROUTINE_SCHEMA='%s'", dbName));
            while (rs.next()) {
                listSp.add(new StoredProceduresBean(rs.getString("SPECIFIC_NAME")));
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
            rs = st.executeQuery(String.format("SHOW CREATE PROCEDURE %s.%s;", dbName, spName));
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
            st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
            if (queryTimeoutSeconds > 0) {
                st.setQueryTimeout(queryTimeoutSeconds);
            }
            //选择一个数据库
            st.execute("use ".concat(dbName));
            //按【;】拆分SQL执行，默认最后一条为查询语句，为了方便使用SET @变量 = XXX
            sql = sql.replace("\n"," ");
            sql = sql.replace("\r"," ");
            String[] splitSql = sql.split(";");
            for (int i = 0; i < splitSql.length - 1; i++) {
                st.execute(String.format("%s;", splitSql[i]));
            }

            //执行sql
            rs = st.executeQuery(String.format("%s;", splitSql[splitSql.length - 1]));
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
                Map<String, Object> rowData = new LinkedHashMap<>();
                for(int i=1;i<=columnCount;i++){
                    String columnTypeName = md.getColumnTypeName(i);
                    Object object;
                    // MySQL DATETIME 不带时区语义，避免走 Timestamp 触发 8 小时换算
                    if (isMysqlDateTimeColumn(columnTypeName)) {
                        object = formatMysqlDateTime(rs, i);
                    } else if (md.getColumnType(i) == Types.TIMESTAMP) {
                        object = rs.getObject(i);
                        object = object == null ? "NULL" : String.valueOf(rs.getTimestamp(i));
                    } else {
                        object = rs.getObject(i);
                    }
                    rowData.put(md.getColumnLabel(i), object);
                }
                listData.add(rowData);
            }

            result[1] = listData.size();
            result[2] = listData;
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
