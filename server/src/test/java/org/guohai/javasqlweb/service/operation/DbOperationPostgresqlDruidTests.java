package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.PoolStatBean;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.TargetSessionStatBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbOperationPostgresqlDruidTests {

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
