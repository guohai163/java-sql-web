package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlIdentifierUtilsTests {

    @Test
    void quoteIdentifiersEscapesDialectSpecificClosingCharacters() {
        assertEquals("`ana``lytics;--DROP`", SqlIdentifierUtils.quoteMysqlIdentifier("ana`lytics;--DROP"));
        assertEquals("[report]]db;/*DROP*/]", SqlIdentifierUtils.quoteMssqlIdentifier("report]db;/*DROP*/"));
        assertEquals("\"public\"\"schema;--DROP\"", SqlIdentifierUtils.quotePostgresqlIdentifier("public\"schema;--DROP"));
    }

    @Test
    void normalizeIdentifierRejectsEmptyAndControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifierUtils.normalizeIdentifier(" "));
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifierUtils.normalizeIdentifier("demo\nname"));
    }
}
