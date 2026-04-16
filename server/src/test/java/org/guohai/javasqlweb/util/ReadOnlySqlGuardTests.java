package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReadOnlySqlGuardTests {

    @Test
    void shouldAllowReadOnlyMultiSelectStatements() {
        assertNull(ReadOnlySqlGuard.validate("SELECT 1; SELECT 2;", "mysql"));
    }

    @Test
    void shouldAllowMysqlVariableAssignmentStatements() {
        assertNull(ReadOnlySqlGuard.validate("SET @x = 1; SELECT @x;", "mysql"));
    }

    @Test
    void shouldAllowMssqlVariableAssignmentStatements() {
        assertNull(ReadOnlySqlGuard.validate("DECLARE @x INT; SET @x = 1; SELECT @x;", "mssql"));
    }

    @Test
    void shouldRejectWriteStatementsInMultiQuery() {
        assertEquals(
                "仅允许只读查询；多语句中包含不允许的子语句",
                ReadOnlySqlGuard.validate("SELECT 1; DELETE FROM user_tb;", "mysql")
        );
    }

    @Test
    void shouldRejectSessionLevelMysqlSetStatements() {
        assertEquals(
                "仅允许只读查询；多语句中包含不允许的子语句",
                ReadOnlySqlGuard.validate("SET sql_safe_updates=0; SELECT 1;", "mysql")
        );
    }

    @Test
    void shouldRejectVariableStatementsForPostgresql() {
        assertEquals(
                "仅允许只读查询；多语句中包含不允许的子语句",
                ReadOnlySqlGuard.validate("SET @x = 1; SELECT 1;", "postgresql")
        );
    }
}
