package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbeServiceTests {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Test
    void checkLivenessShouldAlwaysSucceed() {
        ProbeService probeService = new ProbeService(dataSource);

        Result<String> result = probeService.checkLiveness();

        assertTrue(result.getStatus());
        assertEquals("alive", result.getData());
    }

    @Test
    void checkReadinessShouldSucceedWhenMainDataSourceIsValid() throws Exception {
        ProbeService probeService = new ProbeService(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        Result<String> result = probeService.checkReadiness();

        assertTrue(result.getStatus());
        assertEquals("ready", result.getData());
    }

    @Test
    void checkReadinessShouldFailWhenMainDataSourceConnectionThrows() throws Exception {
        ProbeService probeService = new ProbeService(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("main db down"));

        Result<String> result = probeService.checkReadiness();

        assertFalse(result.getStatus());
        assertEquals("main db down", result.getMessage());
    }
}
