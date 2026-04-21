package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.PoolStatBean;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
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
    void queryDatabaseBySqlAppliesConfiguredTimeoutToAllStatements() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        DbOperationMysqlDruid operation = new DbOperationMysqlDruid(dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)).thenReturn(statement);
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
        Object[] result = operation.queryDatabaseBySql("analytics", "SET @x = 1; SELECT @x", 10);

        assertEquals(1, result[1]);
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
}
