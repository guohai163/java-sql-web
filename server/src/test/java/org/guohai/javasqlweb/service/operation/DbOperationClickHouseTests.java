package org.guohai.javasqlweb.service.operation;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbOperationClickHouseTests {

    @Test
    void queryDatabaseBySqlUsesForwardOnlyTraversalAndKeepsTruncationHint() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        DbOperationClickHouse operation = new DbOperationClickHouse(dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);
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
        verify(statement).execute("use analytics");
        verify(statement).executeQuery("select * from demo;");
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void queryDatabaseBySqlKeepsTimestampFormattingWhenResultIsWithinLimit() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        DbOperationClickHouse operation = new DbOperationClickHouse(dataSource);
        Timestamp timestamp = Timestamp.valueOf("2026-04-20 12:34:56");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);
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
    }
}
