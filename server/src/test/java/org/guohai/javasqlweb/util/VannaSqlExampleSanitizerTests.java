package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VannaSqlExampleSanitizerTests {

    @Test
    void shouldSanitizeSensitiveLiterals() {
        String sql = "SELECT * FROM order_tb WHERE mobile = '13800138000' AND created_at >= '2026-06-23 10:20:30' AND id IN (1,2,3,4,5)";
        String sanitized = VannaSqlExampleSanitizer.sanitize(sql);

        assertEquals("SELECT * FROM order_tb WHERE mobile = '?' AND created_at >= '?' AND id IN (?, ?, ?, ...)", sanitized);
    }

    @Test
    void shouldAcceptReadOnlySelect() {
        assertTrue(VannaSqlExampleSanitizer.looksReadableSelect("SELECT * FROM demo LIMIT 10", "mysql"));
    }

    @Test
    void shouldRejectMutationSql() {
        assertFalse(VannaSqlExampleSanitizer.looksReadableSelect("DELETE FROM demo WHERE id = 1", "mysql"));
    }
}
