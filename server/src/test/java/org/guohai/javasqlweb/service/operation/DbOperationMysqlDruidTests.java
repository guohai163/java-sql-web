package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.PoolStatBean;
import org.guohai.javasqlweb.beans.QueryExecutionResult;
import org.guohai.javasqlweb.beans.TablesNameBean;
import org.guohai.javasqlweb.beans.TargetSessionStatBean;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbOperationMysqlDruidTests {

    @Test
    void getTableListOrdersByTableNameAscending() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        DbOperationMysqlDruid operation = new DbOperationMysqlDruid(dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("table_name")).thenReturn("A_table");
        when(resultSet.getLong("table_rows")).thenReturn(123L);

        List<TablesNameBean> tables = operation.getTableList("analytics");

        assertEquals(1, tables.size());
        assertEquals("A_table", tables.get(0).getTableName());
        assertEquals(123L, tables.get(0).getTableRows());
        verify(statement).executeQuery(
                "SELECT table_name ,table_rows FROM `information_schema`.`tables` WHERE TABLE_SCHEMA = 'analytics' ORDER BY table_name ASC;");
    }

    @Test
    void queryDatabaseBySqlAppliesConfiguredTimeoutToAllStatements() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        Statement sessionStatement = mock(Statement.class);
        ResultSet sessionResultSet = mock(ResultSet.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        DbOperationMysqlDruid operation = new DbOperationMysqlDruid(dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(sessionStatement);
        when(connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)).thenReturn(statement);
        when(sessionStatement.executeQuery(anyString())).thenReturn(sessionResultSet);
        when(sessionResultSet.next()).thenReturn(true);
        when(sessionResultSet.getString("value")).thenReturn("901");
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("value");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaData.getColumnTypeName(1)).thenReturn("INT");
        when(resultSet.getRow()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(1);

        operation.configureQueryTimeoutSeconds(30);
        QueryExecutionResult executionResult = operation.queryDatabaseBySqlWithSession("analytics", "SET @x = 1; SELECT @x", 10, null);
        Object[] result = executionResult.getRows();

        assertEquals(1, result[1]);
        assertEquals("901", executionResult.getDbSessionId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result[2];
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).get("value"));
        verify(statement).setQueryTimeout(30);
        verify(statement).execute("use analytics");
        verify(statement).execute("SET @x = 1;");
        verify(statement).executeQuery(" SELECT @x;");
    }

    @Test
    void describeRuntimePoolReturnsHikariMetrics() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean poolMxBean = mock(HikariPoolMXBean.class);
        DbOperationMysqlDruid operation = new DbOperationMysqlDruid(dataSource);

        when(dataSource.getPoolName()).thenReturn("jsw-mysql-991");
        when(dataSource.getJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/demo");
        when(dataSource.getDriverClassName()).thenReturn("com.mysql.cj.jdbc.Driver");
        when(dataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
        when(poolMxBean.getActiveConnections()).thenReturn(5);
        when(poolMxBean.getIdleConnections()).thenReturn(3);
        when(poolMxBean.getTotalConnections()).thenReturn(8);
        when(poolMxBean.getThreadsAwaitingConnection()).thenReturn(2);

        PoolStatBean stat = operation.describeRuntimePool();

        assertNotNull(stat);
        assertEquals("jsw-mysql-991", stat.getPoolName());
        assertEquals(5, stat.getActiveConnections());
        assertEquals(3, stat.getIdleConnections());
        assertEquals(8, stat.getTotalConnections());
        assertEquals(2, stat.getThreadsAwaitingConnection());
    }

    @Test
    void listActiveSessionsMapsMysqlProcesslistRows() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        DbOperationMysqlDruid operation = new DbOperationMysqlDruid(buildConnectConfig());
        java.lang.reflect.Field dataSourceField = DbOperationMysqlDruid.class.getDeclaredField("sqlDs");
        dataSourceField.setAccessible(true);
        dataSourceField.set(operation, dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("session_id")).thenReturn("77");
        when(resultSet.getString("database_user_name")).thenReturn("report_user");
        when(resultSet.getString("client_host")).thenReturn("10.0.0.9");
        when(resultSet.getString("database_name")).thenReturn("analytics");
        when(resultSet.getString("session_status")).thenReturn("executing");
        when(resultSet.getString("command_or_wait")).thenReturn("Query");
        when(resultSet.getObject("running_seconds")).thenReturn(12L);
        when(resultSet.getString("sql_text")).thenReturn("select * from orders");

        List<TargetSessionStatBean> sessions = operation.listActiveSessions();

        assertEquals(1, sessions.size());
        assertEquals("77", sessions.get(0).getSessionId());
        assertEquals("report_user", sessions.get(0).getDatabaseUserName());
        assertEquals("select * from orders", sessions.get(0).getSqlText());
    }

    private org.guohai.javasqlweb.beans.ConnectConfigBean buildConnectConfig() {
        org.guohai.javasqlweb.beans.ConnectConfigBean bean = new org.guohai.javasqlweb.beans.ConnectConfigBean();
        bean.setCode(991);
        bean.setDbServerHost("127.0.0.1");
        bean.setDbServerPort("3306");
        bean.setDbServerUsername("root");
        bean.setDbServerPassword("root");
        bean.setDbServerType("mysql");
        return bean;
    }
}
