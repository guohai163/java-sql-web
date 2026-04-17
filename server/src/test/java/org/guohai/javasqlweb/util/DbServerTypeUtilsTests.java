package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbServerTypeUtilsTests {

    @Test
    void shouldNormalizeLegacyAliases() {
        assertEquals("clickhouse", DbServerTypeUtils.normalize("clickhouce"));
        assertEquals("postgresql", DbServerTypeUtils.normalize("pgsql"));
        assertEquals("mssql", DbServerTypeUtils.normalize("mssql_druid"));
    }

    @Test
    void shouldTreatMariaDbAsMysqlFamily() {
        assertTrue(DbServerTypeUtils.isMysqlFamily("mariadb"));
    }
}
