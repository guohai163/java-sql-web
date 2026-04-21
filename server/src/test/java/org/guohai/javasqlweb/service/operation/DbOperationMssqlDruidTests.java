package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.PoolStatBean;
import org.guohai.javasqlweb.beans.TargetSessionStatBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbOperationMssqlDruidTests {

    @Test
    void describeRuntimePoolReturnsHikariMetrics() throws Exception {
        DbOperationMssqlDruid operation = new DbOperationMssqlDruid(buildConnectConfig());
        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean poolMxBean = mock(HikariPoolMXBean.class);
        try {
            Field field = DbOperationMssqlDruid.class.getDeclaredField("sqlDs");
            field.setAccessible(true);
            field.set(operation, dataSource);

            when(dataSource.getPoolName()).thenReturn("jsw-mssql-11");
            when(dataSource.getJdbcUrl()).thenReturn("jdbc:sqlserver://127.0.0.1:1433;encrypt=true");
            when(dataSource.getDriverClassName()).thenReturn("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            when(dataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
            when(poolMxBean.getActiveConnections()).thenReturn(4);
            when(poolMxBean.getIdleConnections()).thenReturn(6);
            when(poolMxBean.getTotalConnections()).thenReturn(10);
            when(poolMxBean.getThreadsAwaitingConnection()).thenReturn(2);

            PoolStatBean stat = operation.describeRuntimePool();

            assertNotNull(stat);
            assertEquals("jsw-mssql-11", stat.getPoolName());
            assertEquals(4, stat.getActiveConnections());
            assertEquals(6, stat.getIdleConnections());
            assertEquals(10, stat.getTotalConnections());
            assertEquals(2, stat.getThreadsAwaitingConnection());
        } finally {
            operation.close();
        }
    }

    @Test
    void listActiveSessionsMapsMssqlDmvs() throws Exception {
        DbOperationMssqlDruid operation = new DbOperationMssqlDruid(buildConnectConfig());
        javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        try {
            Field field = DbOperationMssqlDruid.class.getDeclaredField("sqlDs");
            field.setAccessible(true);
            field.set(operation, dataSource);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString("session_id")).thenReturn("88");
            when(resultSet.getString("database_user_name")).thenReturn("report_user");
            when(resultSet.getString("client_host")).thenReturn("10.0.0.8");
            when(resultSet.getString("database_name")).thenReturn("report_db");
            when(resultSet.getString("session_status")).thenReturn("running");
            when(resultSet.getString("command_or_wait")).thenReturn("CXPACKET");
            when(resultSet.getObject("running_seconds")).thenReturn(9L);
            when(resultSet.getString("sql_text")).thenReturn("select count(*) from report_db.orders");

            List<TargetSessionStatBean> sessions = operation.listActiveSessions();

            assertEquals(1, sessions.size());
            assertEquals("88", sessions.get(0).getSessionId());
            assertEquals("report_user", sessions.get(0).getDatabaseUserName());
            assertEquals("select count(*) from report_db.orders", sessions.get(0).getSqlText());
        } finally {
            operation.close();
        }
    }

    private org.guohai.javasqlweb.beans.ConnectConfigBean buildConnectConfig() {
        org.guohai.javasqlweb.beans.ConnectConfigBean bean = new org.guohai.javasqlweb.beans.ConnectConfigBean();
        bean.setCode(11);
        bean.setDbServerHost("127.0.0.1");
        bean.setDbServerPort("1433");
        bean.setDbServerUsername("sa");
        bean.setDbServerPassword("password");
        bean.setDbServerType("mssql");
        return bean;
    }
}
