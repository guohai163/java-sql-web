package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbOperationClickHouseTests {

    @Test
    void queryDatabaseBySqlUsesDatabaseScopedDatasourceAndKeepsTruncationHint() throws Exception {
        DataSource serverDataSource = mock(DataSource.class);
        DataSource queryDataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        DbOperationClickHouse operation = new DbOperationClickHouse(serverDataSource, null, dbName -> queryDataSource);

        when(queryDataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(metaData.getColumnLabel(2)).thenReturn("name");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaData.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2);
        when(resultSet.getObject(2)).thenReturn("alpha", "beta");

        Object[] result = operation.queryDatabaseBySql("analytics", "select * from demo", 2);

        assertEquals(3, result[0]);
        assertEquals(2, result[1]);
        assertInstanceOf(List.class, result[2]);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result[2];
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).get("id"));
        assertEquals("beta", rows.get(1).get("name"));
        verify(connection).createStatement();
        verify(connection, never()).createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        verify(queryDataSource).getConnection();
        verify(statement, never()).execute(anyString());
        verify(statement).executeQuery("select * from demo;");
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
        verifyNoInteractions(serverDataSource);
    }

    @Test
    void queryDatabaseBySqlKeepsTimestampFormattingWhenResultIsWithinLimit() throws Exception {
        DataSource serverDataSource = mock(DataSource.class);
        DataSource queryDataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        DbOperationClickHouse operation = new DbOperationClickHouse(serverDataSource, null, dbName -> queryDataSource);
        Timestamp timestamp = Timestamp.valueOf("2026-04-20 12:34:56");

        when(queryDataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("created_at");
        when(metaData.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(timestamp);
        when(resultSet.getTimestamp(1)).thenReturn(timestamp);

        Object[] result = operation.queryDatabaseBySql("analytics", "select now()", 10);

        assertEquals(1, result[0]);
        assertEquals(1, result[1]);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result[2];
        assertEquals(1, rows.size());
        assertEquals(String.valueOf(timestamp), rows.get(0).get("created_at"));
        assertTrue(((Integer) result[0]) == ((Integer) result[1]));
        verify(statement, never()).execute(anyString());
    }

    @Test
    void queryDatabaseBySqlReusesScopedDatasourceForSameDatabase() throws Exception {
        DataSource queryDataSource = mock(DataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        Statement firstStatement = mock(Statement.class);
        Statement secondStatement = mock(Statement.class);
        ResultSet firstResultSet = mock(ResultSet.class);
        ResultSet secondResultSet = mock(ResultSet.class);
        ResultSetMetaData firstMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData secondMetaData = mock(ResultSetMetaData.class);
        AtomicInteger createCounter = new AtomicInteger();
        DbOperationClickHouse operation = new DbOperationClickHouse(mock(DataSource.class), null, dbName -> {
            createCounter.incrementAndGet();
            return queryDataSource;
        });

        when(queryDataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(firstConnection.createStatement()).thenReturn(firstStatement);
        when(secondConnection.createStatement()).thenReturn(secondStatement);
        when(firstStatement.executeQuery(anyString())).thenReturn(firstResultSet);
        when(secondStatement.executeQuery(anyString())).thenReturn(secondResultSet);
        when(firstResultSet.getMetaData()).thenReturn(firstMetaData);
        when(secondResultSet.getMetaData()).thenReturn(secondMetaData);
        when(firstMetaData.getColumnCount()).thenReturn(1);
        when(secondMetaData.getColumnCount()).thenReturn(1);
        when(firstMetaData.getColumnLabel(1)).thenReturn("value");
        when(secondMetaData.getColumnLabel(1)).thenReturn("value");
        when(firstMetaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(secondMetaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(firstResultSet.next()).thenReturn(true, false);
        when(secondResultSet.next()).thenReturn(true, false);
        when(firstResultSet.getObject(1)).thenReturn(1);
        when(secondResultSet.getObject(1)).thenReturn(2);

        operation.queryDatabaseBySql("analytics", "select 1", 10);
        operation.queryDatabaseBySql("analytics", "select 2", 10);

        assertEquals(1, createCounter.get());
        verify(firstStatement).executeQuery("select 1;");
        verify(secondStatement).executeQuery("select 2;");
    }

    @Test
    void getColumnsListMapsAliasedClickHouseColumnsAndNullableState() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        DbOperationClickHouse operation = new DbOperationClickHouse(dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("column_name")).thenReturn("user_id", "remark");
        when(resultSet.getString("column_type")).thenReturn("UInt64", "Nullable(String)");
        when(resultSet.getString("column_comment")).thenReturn(null, "备注");

        List<org.guohai.javasqlweb.beans.ColumnsNameBean> columns =
                operation.getColumnsList("dw_game_wd", "dw_scan_stall_result");

        assertEquals(2, columns.size());
        assertEquals("user_id", columns.get(0).getColumnName());
        assertEquals("UInt64", columns.get(0).getColumnType());
        assertEquals("", columns.get(0).getColumnComment());
        assertEquals("not null", columns.get(0).getColumnIsNull());
        assertEquals("remark", columns.get(1).getColumnName());
        assertEquals("Nullable(String)", columns.get(1).getColumnType());
        assertEquals("备注", columns.get(1).getColumnComment());
        assertEquals("null", columns.get(1).getColumnIsNull());
        verify(statement).executeQuery(
                "SELECT name AS column_name, type AS column_type, comment AS column_comment " +
                        "FROM system.columns WHERE database='dw_game_wd' AND table='dw_scan_stall_result' LIMIT 100;");
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void cleanupQueryDataSourcesClosesIdlePools() throws Exception {
        DbOperationClickHouse operation = new DbOperationClickHouse(mock(DataSource.class), buildConnectConfig(), dbName -> mock(HikariDataSource.class));
        HikariDataSource idleDataSource = mock(HikariDataSource.class);
        HikariDataSource activeDataSource = mock(HikariDataSource.class);
        Map<String, Object> queryDataSourceMap = accessQueryDataSourceMap(operation);
        long now = System.currentTimeMillis();

        queryDataSourceMap.put("archive", newCachedDataSource(idleDataSource, now - 20 * 60 * 1000L));
        queryDataSourceMap.put("core", newCachedDataSource(activeDataSource, now));

        Method cleanupMethod = DbOperationClickHouse.class.getDeclaredMethod("cleanupQueryDataSources", long.class, String.class);
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(operation, now, "core");

        assertFalse(queryDataSourceMap.containsKey("archive"));
        assertTrue(queryDataSourceMap.containsKey("core"));
        verify(idleDataSource).close();
    }

    @Test
    void cleanupQueryDataSourcesEvictsOldestPoolWhenLimitExceeded() throws Exception {
        DbOperationClickHouse operation = new DbOperationClickHouse(mock(DataSource.class), buildConnectConfig(), dbName -> mock(HikariDataSource.class));
        Map<String, Object> queryDataSourceMap = accessQueryDataSourceMap(operation);
        long now = System.currentTimeMillis();

        for (int index = 0; index < 17; index++) {
            HikariDataSource dataSource = mock(HikariDataSource.class);
            queryDataSourceMap.put("db_" + index, newCachedDataSource(dataSource, now - 1_000L - index));
        }

        Method cleanupMethod = DbOperationClickHouse.class.getDeclaredMethod("cleanupQueryDataSources", long.class, String.class);
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(operation, now, null);

        assertEquals(16, queryDataSourceMap.size());
        assertFalse(queryDataSourceMap.containsKey("db_16"));
    }

    @Test
    void closeClosesServerAndCachedQueryPools() throws Exception {
        HikariDataSource serverDataSource = mock(HikariDataSource.class);
        DbOperationClickHouse operation = new DbOperationClickHouse(serverDataSource, buildConnectConfig(), dbName -> mock(HikariDataSource.class));
        HikariDataSource queryDataSource = mock(HikariDataSource.class);
        accessQueryDataSourceMap(operation).put("analytics", newCachedDataSource(queryDataSource, System.currentTimeMillis()));

        operation.close();

        verify(serverDataSource).close();
        verify(queryDataSource).close();
        assertTrue(accessQueryDataSourceMap(operation).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> accessQueryDataSourceMap(DbOperationClickHouse operation) throws Exception {
        Field field = DbOperationClickHouse.class.getDeclaredField("queryDataSourceMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(operation);
    }

    private Object newCachedDataSource(HikariDataSource dataSource, long lastAccessAt) throws Exception {
        Class<?> cachedDataSourceClass = Class.forName(DbOperationClickHouse.class.getName() + "$CachedDataSource");
        Constructor<?> constructor = cachedDataSourceClass.getDeclaredConstructor(DataSource.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource, lastAccessAt);
    }

    private ConnectConfigBean buildConnectConfig() {
        ConnectConfigBean bean = new ConnectConfigBean();
        bean.setCode(1);
        bean.setDbServerHost("127.0.0.1");
        bean.setDbServerPort("8123");
        bean.setDbServerUsername("default");
        bean.setDbServerPassword("");
        bean.setDbServerType("clickhouse");
        return bean;
    }
}
