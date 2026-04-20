package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MssqlQueryBatchParserTests {

    @Test
    void shouldExtractLeadingUseWithoutSemicolon() {
        MssqlQueryBatchParser.ParsedBatch parsedBatch = MssqlQueryBatchParser.parse(
                "USE [TreasureWDDB_History]\nDECLARE @x INT = 1;\nSELECT @x;",
                "fallback_db"
        );

        assertTrue(parsedBatch.isValid());
        assertEquals("TreasureWDDB_History", parsedBatch.getEffectiveDbName());
        assertEquals("DECLARE @x INT = 1;\nSELECT @x;", parsedBatch.getSqlWithoutUse().trim());
    }

    @Test
    void shouldRejectMultipleUseStatements() {
        MssqlQueryBatchParser.ParsedBatch parsedBatch = MssqlQueryBatchParser.parse(
                "USE [TreasureWDDB_History];\nUSE archive_db;\nSELECT 1;",
                "fallback_db"
        );

        assertFalse(parsedBatch.isValid());
        assertEquals("MSSQL 仅支持在批处理开头使用单个 USE 语句", parsedBatch.getErrorMessage());
    }

    @Test
    void shouldRejectStandaloneUseLaterInBatch() {
        MssqlQueryBatchParser.ParsedBatch parsedBatch = MssqlQueryBatchParser.parse(
                "DECLARE @x INT = 1;\nUSE archive_db\nSELECT @x;",
                "fallback_db"
        );

        assertFalse(parsedBatch.isValid());
        assertEquals("MSSQL 仅支持在批处理开头使用单个 USE 语句", parsedBatch.getErrorMessage());
    }
}
