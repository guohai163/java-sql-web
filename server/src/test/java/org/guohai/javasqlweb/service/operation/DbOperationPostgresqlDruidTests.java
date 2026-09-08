package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.PoolStatBean;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.TablesNameBean;
import org.guohai.javasqlweb.beans.TargetSessionStatBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;

class DbOperationPostgresqlDruidTests {

    @Test
    void getTableListOrdersByTableNameAscending() throws Exception {
        DbOperationPostgresqlDruid operation = new DbOperationPostgresqlDruid(buildConnectConfig());
        com.zaxxer.hikari.HikariDataSource dataSource = mock(com.zaxxer.hikari.HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        long now = System.currentTimeMillis();
        try {
            accessPostgresMap(operation).put("demo", newCachedDataSource(mock(com.zaxxer.hikari.HikariDataSource.class), now));
            accessPostgresMap(operation).put("analytics", newCachedDataSource(dataSource, now));

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString("table_name")).thenReturn("A_table");
            when(resultSet.getLong("row_counts")).thenReturn(88L);

            java.util.List<TablesNameBean> tables = operation.getTableList("analytics");

            assertEquals(1, tables.size());
            assertEquals("A_table", tables.get(0).getTableName());
            assertEquals(88L, tables.get(0).getTableRows());
            verify(statement).setString(1, "public");
            verify(statement).executeQuery();
        } finally {
            operation.close();
        }
    }

    @Test
    void queryDatabaseBySqlAppliesPostgresqlQueryControlsWithoutScrollableCursor() throws Exception {
        DbOperationPostgresqlDruid operation = new DbOperationPostgresqlDruid(buildConnectConfig());
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        Statement sessionStatement = mock(Statement.class);
        Statement queryStatement = mock(Statement.class);
        ResultSet sessionResultSet = mock(ResultSet.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        long now = System.currentTimeMillis();
        try {
            accessPostgresMap(operation).put("analytics", newCachedDataSource(dataSource, now));
            operation.configureQueryTimeoutSeconds(15);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(sessionStatement);
            when(connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).thenReturn(queryStatement);
            when(sessionStatement.executeQuery(anyString())).thenReturn(sessionResultSet);
            when(sessionResultSet.next()).thenReturn(true);
            when(sessionResultSet.getString("value")).thenReturn("321");
            when(queryStatement.executeQuery(anyString())).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(metaData);
            when(metaData.getColumnCount()).thenReturn(1);
            when(metaData.getColumnLabel(1)).thenReturn("value");
            when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getObject(1)).thenReturn(1, 2);

            org.guohai.javasqlweb.beans.QueryExecutionResult executionResult =
                    operation.queryDatabaseBySqlWithSession("analytics", "SELECT value FROM t", 1, null);

            Object[] result = executionResult.getRows();
            assertEquals(2, result[0]);
            assertEquals(1, result[1]);
            assertEquals("321", executionResult.getDbSessionId());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result[2];
            assertEquals(1, rows.size());
            verify(queryStatement).setQueryTimeout(15);
            verify(queryStatement).setMaxRows(2);
            verify(queryStatement).setFetchSize(2);
            verify(queryStatement).executeQuery("SELECT value FROM t;");
            verify(connection, never()).createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            verify(resultSet, never()).last();
        } finally {
            operation.close();
        }
    }

    @Test
    void cleanupCachedDataSourcesClosesIdlePools() throws Exception {
        DbOperationPostgresqlDruid operation = new DbOperationPostgresqlDruid(buildConnectConfig());
        try {
            HikariDataSource idleDataSource = mock(HikariDataSource.class);
            HikariDataSource activeDataSource = mock(HikariDataSource.class);
            Map<String, Object> postgresMap = accessPostgresMap(operation);
            long now = System.currentTimeMillis();

            postgresMap.put("archive", newCachedDataSource(idleDataSource, now - 20 * 60 * 1000L));
            postgresMap.put("core", newCachedDataSource(activeDataSource, now));

            Method cleanupMethod = DbOperationPostgresqlDruid.class
                    .getDeclaredMethod("cleanupCachedDataSources", long.class, String.class);
            cleanupMethod.setAccessible(true);
            cleanupMethod.invoke(operation, now, "core");

            assertFalse(postgresMap.containsKey("archive"));
            assertTrue(postgresMap.containsKey("core"));
            verify(idleDataSource).close();
        } finally {
            operation.close();
        }
    }

    @Test
    void describeRuntimePoolAggregatesCachedDatabasePools() throws Exception {
        DbOperationPostgresqlDruid operation = new DbOperationPostgresqlDruid(buildConnectConfig());
        try {
            HikariDataSource coreDataSource = mock(HikariDataSource.class);
            HikariDataSource archiveDataSource = mock(HikariDataSource.class);
            HikariPoolMXBean corePool = mock(HikariPoolMXBean.class);
            HikariPoolMXBean archivePool = mock(HikariPoolMXBean.class);
            Map<String, Object> postgresMap = accessPostgresMap(operation);
            long now = System.currentTimeMillis();

            when(coreDataSource.getPoolName()).thenReturn("jsw-postgresql-core");
            when(coreDataSource.getJdbcUrl()).thenReturn("jdbc:postgresql://127.0.0.1:5432/core");
            when(coreDataSource.getDriverClassName()).thenReturn("org.postgresql.Driver");
            when(coreDataSource.getHikariPoolMXBean()).thenReturn(corePool);
            when(corePool.getActiveConnections()).thenReturn(2);
            when(corePool.getIdleConnections()).thenReturn(3);
            when(corePool.getTotalConnections()).thenReturn(5);
            when(corePool.getThreadsAwaitingConnection()).thenReturn(1);

            when(archiveDataSource.getPoolName()).thenReturn("jsw-postgresql-archive");
            when(archiveDataSource.getJdbcUrl()).thenReturn("jdbc:postgresql://127.0.0.1:5432/archive");
            when(archiveDataSource.getDriverClassName()).thenReturn("org.postgresql.Driver");
            when(archiveDataSource.getHikariPoolMXBean()).thenReturn(archivePool);
            when(archivePool.getActiveConnections()).thenReturn(1);
            when(archivePool.getIdleConnections()).thenReturn(4);
            when(archivePool.getTotalConnections()).thenReturn(5);
            when(archivePool.getThreadsAwaitingConnection()).thenReturn(0);

            postgresMap.put("core", newCachedDataSource(coreDataSource, now));
            postgresMap.put("archive", newCachedDataSource(archiveDataSource, now));

            PoolStatBean stat = operation.describeRuntimePool();

            assertNotNull(stat);
            assertEquals("jsw-postgresql-core +1", stat.getPoolName());
            assertEquals(3, stat.getActiveConnections());
            assertEquals(7, stat.getIdleConnections());
            assertEquals(10, stat.getTotalConnections());
            assertEquals(1, stat.getThreadsAwaitingConnection());
        } finally {
            operation.close();
        }
    }

    @Test
    void listActiveSessionsMapsPgStatActivityRows() throws Exception {
        DbOperationPostgresqlDruid operation = new DbOperationPostgresqlDruid(buildConnectConfig());
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        try {
            Field dataSourceField = DbOperationPostgresqlDruid.class.getDeclaredField("sqlDs");
            dataSourceField.setAccessible(true);
            javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
            dataSourceField.set(operation, dataSource);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString("session_id")).thenReturn("321");
            when(resultSet.getString("database_user_name")).thenReturn("analytics_user");
            when(resultSet.getString("client_host")).thenReturn("10.20.1.13");
            when(resultSet.getString("database_name")).thenReturn("demo");
            when(resultSet.getString("session_status")).thenReturn("active");
            when(resultSet.getString("command_or_wait")).thenReturn("Lock:transactionid");
            when(resultSet.getObject("running_seconds")).thenReturn(18L);
            when(resultSet.getString("sql_text")).thenReturn("select * from demo.orders");

            java.util.List<TargetSessionStatBean> sessions = operation.listActiveSessions();

            assertEquals(1, sessions.size());
            assertEquals("321", sessions.get(0).getSessionId());
            assertEquals("analytics_user", sessions.get(0).getDatabaseUserName());
            assertEquals("select * from demo.orders", sessions.get(0).getSqlText());
        } finally {
            operation.close();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> accessPostgresMap(DbOperationPostgresqlDruid operation) throws Exception {
        Field field = DbOperationPostgresqlDruid.class.getDeclaredField("postgresMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(operation);
    }

    private Object newCachedDataSource(HikariDataSource dataSource, long lastAccessAt) throws Exception {
        Class<?> cachedDataSourceClass = Class.forName(DbOperationPostgresqlDruid.class.getName() + "$CachedDataSource");
        Constructor<?> constructor = cachedDataSourceClass.getDeclaredConstructor(javax.sql.DataSource.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource, lastAccessAt);
    }

    private ConnectConfigBean buildConnectConfig() {
        ConnectConfigBean bean = new ConnectConfigBean();
        bean.setCode(1);
        bean.setDbServerHost("127.0.0.1");
        bean.setDbServerPort("5432");
        bean.setDbServerUsername("postgres");
        bean.setDbServerPassword("postgres");
        bean.setDbServerType("postgresql");
        return bean;
    }
}
