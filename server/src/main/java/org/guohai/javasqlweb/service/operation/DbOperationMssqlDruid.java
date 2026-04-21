package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.guohai.javasqlweb.util.Utils.closeResource;

/**
 * 基于alibaba druid连接池的mysql实现类
 * @author guohai
 * @date 2021-1-5
 */
public class DbOperationMssqlDruid implements DbOperation {

    private static final String SSL_MODE_DEFAULT = "DEFAULT";
    private static final String SSL_MODE_DISABLE_ENCRYPTION = "DISABLE_ENCRYPTION";
    private static final String SSL_MODE_LEGACY_TLS = "LEGACY_TLS";

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(DbOperationMssqlDruid.class);

    /**
     * 数据源
     */
    private DataSource sqlDs;

    private final String applicationName;


    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationMssqlDruid(ConnectConfigBean conn) throws Exception {
        applicationName = "jsw-mssql-" + conn.getCode();
        sqlDs = HikariDataSourceUtils.createDataSource(
                applicationName,
                buildJdbcUrl(conn),
                conn.getDbServerUsername(),
                conn.getDbServerPassword(),
                "select getdate()"
        );
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
        ResultSet rs = st.executeQuery("SELECT database_id,name FROM sys.databases ;");
        while (rs.next()){
            listDnb.add(new DatabaseNameBean(rs.getObject("name").toString()));
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
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT a.name, b.rows FROM sysobjects a JOIN sysindexes b ON a.id = b.id " +
                "WHERE xtype = 'u' and indid in (0,1) ORDER BY a.name;", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getObject("name").toString(),
                    rs.getLong("rows")));
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
        List<ViewNameBean> listTnb = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT name FROM sysobjects WHERE xtype = 'V' ORDER BY name;", dbName));
        while (rs.next()){
            listTnb.add(new ViewNameBean(rs.getObject("name").toString()));
        }
        closeResource(rs,st,conn);
        return listTnb;
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
        ViewNameBean viewBean = null;
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "select definition from sys.sql_modules WHERE object_id = object_id('%s')", dbName, viewName));
        while (rs.next()){
            viewBean = new ViewNameBean(viewName, rs.getString("definition"));
        }
        closeResource(rs,st,conn);
        return viewBean;
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
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT b.name column_name,c.name column_type,b.length column_length,b.isnullable is_null_able,d.value column_comment \n" +
                "FROM sysobjects a join syscolumns b on a.id=b.id and a.xtype='U'\n" +
                "join systypes c on b.xtype=c.xusertype\n" +
                "left join sys.extended_properties d on d.major_id=b.id and d.minor_id=b.colid\n" +
                "where a.name='%s'", dbName, tableName));
        while (rs.next()){
            listCnb.add(new ColumnsNameBean(rs.getString("column_name"),
                    rs.getString("column_type"),
                    rs.getString("column_length"),
                    rs.getString("column_comment") == null?"":rs.getString("column_comment"),
                    rs.getInt("is_null_able") == 0?"not null":"null"
                    ));
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
        Map<String, String []> tables = new HashMap<>(10);
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];\n" +
                "    SELECT a.name as table_name,stuff((select ','+ b.name from syscolumns b where a.id=b.id for xml path('')), 1,1,'') as column_name\n" +
                "    FROM sysobjects a --join syscolumns b on a.id=b.id and a.xtype='U'\n" +
                "    where a.xtype='U' group by a.name,a.id;", dbName));
        while (rs.next()){
            tables.put(rs.getString("table_name"), rs.getString("column_name").split(","));
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
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "exec sp_helpindex '%s'", dbName, tableName));
        while (rs.next()){
            listTib.add(new TableIndexesBean(rs.getObject("index_name").toString(),
                    rs.getObject("index_description").toString(),
                    rs.getObject("index_keys").toString()));
        }
        closeResource(rs,st,conn);
        return listTib;
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
        List<StoredProceduresBean> listSp = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT name FROM sysobjects WHERE type='P' ORDER BY name", dbName));
        while (rs.next()){
            listSp.add(new StoredProceduresBean(rs.getString("name")));
        }
        closeResource(rs,st,conn);
        return listSp;
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
        StoredProceduresBean spBean = null;
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "select definition from sys.sql_modules WHERE object_id = object_id('%s')", dbName, spName));
        while (rs.next()){
            spBean = new StoredProceduresBean(spName, rs.getString("definition"));
        }
        closeResource(rs,st,conn);
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
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        st.setMaxRows(limit);
        ResultSet rs = null;
        String sessionId = queryCurrentSessionId(conn);
        if (onSessionReady != null) {
            onSessionReady.accept(sessionId);
        }
        try{
            rs = st.executeQuery(String.format("use [%s];" +
                    "%s;", dbName, sql));
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
                    rowData.put(md.getColumnName(i),rs.getString(i));
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
        }
        QueryExecutionResult executionResult = new QueryExecutionResult();
        executionResult.setDbSessionId(sessionId);
        executionResult.setRows(result);
        return executionResult;
    }



    /**
     * 服务器连接状态健康检查
     * @return
     * @throws SQLException
     */
    @Override
    public Boolean serverHealth() throws SQLException {
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT getdate()");
        closeResource(rs,st,conn);
        return true;
    }

    @Override
    public void close() {
        HikariDataSourceUtils.closeDataSource(sqlDs);
    }

    @Override
    public PoolStatBean describeRuntimePool() {
        HikariDataSource hikariDataSource = unwrapHikariDataSource(sqlDs);
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
                    "SELECT CAST(s.session_id AS nvarchar(20)) AS session_id, " +
                            "       s.login_name AS database_user_name, " +
                            "       s.host_name AS client_host, " +
                            "       DB_NAME(COALESCE(r.database_id, s.database_id)) AS database_name, " +
                            "       COALESCE(r.status, s.status) AS session_status, " +
                            "       COALESCE(r.wait_type, r.command, s.status) AS command_or_wait, " +
                            "       DATEDIFF(SECOND, COALESCE(r.start_time, s.last_request_start_time), SYSDATETIME()) AS running_seconds, " +
                            "       COALESCE(r.start_time, s.last_request_start_time) AS query_start_time, " +
                            "       CONVERT(nvarchar(max), st.text) AS sql_text " +
                            "FROM sys.dm_exec_sessions s " +
                            "LEFT JOIN sys.dm_exec_requests r ON s.session_id = r.session_id " +
                            "OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) st " +
                            "WHERE s.program_name = ? " +
                            "  AND s.session_id <> @@SPID " +
                            "  AND (r.session_id IS NOT NULL OR s.status <> 'sleeping') " +
                            "ORDER BY running_seconds DESC, s.session_id DESC"
            );
            ps.setString(1, applicationName);
            rs = ps.executeQuery();
            while (rs.next()) {
                TargetSessionStatBean bean = new TargetSessionStatBean();
                bean.setDbType("mssql");
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

    private String buildJdbcUrl(ConnectConfigBean conn) {
        String sslMode = conn.getDbSslMode() == null ? SSL_MODE_DEFAULT : conn.getDbSslMode();
        String applicationSegment = String.format(";applicationName=%s", applicationName);
        if (SSL_MODE_DISABLE_ENCRYPTION.equalsIgnoreCase(sslMode)) {
            return String.format("jdbc:sqlserver://%s:%s;encrypt=false%s", conn.getDbServerHost(), conn.getDbServerPort(), applicationSegment);
        }
        if (SSL_MODE_LEGACY_TLS.equalsIgnoreCase(sslMode)) {
            return String.format(
                    "jdbc:sqlserver://%s:%s;encrypt=true;trustServerCertificate=true%s",
                    conn.getDbServerHost(),
                    conn.getDbServerPort(),
                    applicationSegment
            );
        }
        return String.format("jdbc:sqlserver://%s:%s;encrypt=true%s", conn.getDbServerHost(), conn.getDbServerPort(), applicationSegment);
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

    private String queryCurrentSessionId(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT CONVERT(nvarchar(20), @@SPID) AS value")) {
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
