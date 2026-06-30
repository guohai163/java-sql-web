package org.guohai.javasqlweb.service.operation;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbOperationMysqlDruidTest {

    @Test
    void timestampColumnsUseDatabaseTextValue() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("2026-06-30 12:34:56");

        Object value = DbOperationMysqlDruid.readColumnValue(rs, 1, Types.TIMESTAMP);

        assertEquals("2026-06-30 12:34:56", value);
        verify(rs).getString(1);
        verify(rs, never()).getDate(1);
        verify(rs, never()).getTime(1);
    }
}
