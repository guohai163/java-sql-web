package org.guohai.javasqlweb.util;

import java.util.List;

/**
 * Parse MSSQL read-only batches and optionally peel off a leading USE statement.
 */
public final class MssqlQueryBatchParser {

    private static final String INVALID_USE_ERROR = "MSSQL 仅支持在批处理开头使用单个 USE 语句";

    private MssqlQueryBatchParser() {
    }

    public static ParsedBatch parse(String sql, String defaultDbName) {
        if (sql == null || sql.isBlank()) {
            return ParsedBatch.success(defaultDbName, sql == null ? "" : sql);
        }

        LeadingUseStatement leadingUse = parseLeadingUse(sql);
        if (leadingUse.errorMessage != null) {
            return ParsedBatch.error(leadingUse.errorMessage);
        }

        String effectiveDbName = leadingUse.databaseName == null ? defaultDbName : leadingUse.databaseName;
        String sqlWithoutUse = leadingUse.endIndex == 0 ? sql : sql.substring(leadingUse.endIndex);
        if (containsStandaloneUseStatement(sqlWithoutUse)) {
            return ParsedBatch.error(INVALID_USE_ERROR);
        }
        for (String statement : ReadOnlySqlGuard.splitStatements(sqlWithoutUse)) {
            String sanitized = ReadOnlySqlGuard.stripStringsAndComments(statement).trim();
            if (sanitized.isEmpty()) {
                continue;
            }
            if ("USE".equals(ReadOnlySqlGuard.extractFirstKeyword(sanitized))) {
                return ParsedBatch.error(INVALID_USE_ERROR);
            }
        }
        return ParsedBatch.success(effectiveDbName, sqlWithoutUse);
    }

    private static LeadingUseStatement parseLeadingUse(String sql) {
        int start = skipLeadingWhitespaceAndComments(sql, 0);
        if (!startsWithKeyword(sql, start, "USE")) {
            return LeadingUseStatement.none();
        }

        int cursor = start + "USE".length();
        if (cursor >= sql.length() || !Character.isWhitespace(sql.charAt(cursor))) {
            return LeadingUseStatement.error(INVALID_USE_ERROR);
        }
        cursor = skipLeadingWhitespaceAndComments(sql, cursor);
        if (cursor >= sql.length()) {
            return LeadingUseStatement.error(INVALID_USE_ERROR);
        }

        ParsedIdentifier identifier = parseDatabaseIdentifier(sql, cursor);
        if (identifier == null || identifier.databaseName.isBlank()) {
            return LeadingUseStatement.error(INVALID_USE_ERROR);
        }

        int endIndex = findUseStatementEnd(sql, identifier.endIndex);
        if (endIndex < 0) {
            return LeadingUseStatement.error(INVALID_USE_ERROR);
        }
        return new LeadingUseStatement(identifier.databaseName, endIndex, null);
    }

    private static ParsedIdentifier parseDatabaseIdentifier(String sql, int start) {
        if (sql.charAt(start) == '[') {
            int end = sql.indexOf(']', start + 1);
            if (end < 0) {
                return null;
            }
            return new ParsedIdentifier(sql.substring(start + 1, end).trim(), end + 1);
        }

        int cursor = start;
        while (cursor < sql.length() && isBareDatabaseNameChar(sql.charAt(cursor))) {
            cursor++;
        }
        if (cursor == start) {
            return null;
        }
        return new ParsedIdentifier(sql.substring(start, cursor), cursor);
    }

    private static int findUseStatementEnd(String sql, int start) {
        int cursor = start;
        boolean sawNewline = false;
        while (cursor < sql.length()) {
            char currentChar = sql.charAt(cursor);
            if (cursor + 1 < sql.length() && currentChar == '-' && sql.charAt(cursor + 1) == '-') {
                cursor += 2;
                while (cursor < sql.length() && sql.charAt(cursor) != '\n') {
                    cursor++;
                }
                sawNewline = true;
                continue;
            }
            if (currentChar == '#') {
                cursor++;
                while (cursor < sql.length() && sql.charAt(cursor) != '\n') {
                    cursor++;
                }
                sawNewline = true;
                continue;
            }
            if (cursor + 1 < sql.length() && currentChar == '/' && sql.charAt(cursor + 1) == '*') {
                cursor += 2;
                while (cursor + 1 < sql.length() && !(sql.charAt(cursor) == '*' && sql.charAt(cursor + 1) == '/')) {
                    if (sql.charAt(cursor) == '\n' || sql.charAt(cursor) == '\r') {
                        sawNewline = true;
                    }
                    cursor++;
                }
                if (cursor + 1 >= sql.length()) {
                    return sql.length();
                }
                cursor += 2;
                continue;
            }
            if (currentChar == ';') {
                return cursor + 1;
            }
            if (!Character.isWhitespace(currentChar)) {
                return sawNewline ? cursor : -1;
            }
            if (currentChar == '\n' || currentChar == '\r') {
                sawNewline = true;
            }
            cursor++;
        }
        return cursor;
    }

    private static boolean containsStandaloneUseStatement(String sql) {
        String sanitized = ReadOnlySqlGuard.stripStringsAndComments(sql);
        List<String> lines = sanitized.lines().toList();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.regionMatches(true, 0, "USE", 0, "USE".length())) {
                continue;
            }
            if (trimmed.length() > 3
                    && (Character.isLetterOrDigit(trimmed.charAt(3)) || trimmed.charAt(3) == '_')) {
                continue;
            }
            if (!trimmed.contains("=")) {
                return true;
            }
        }
        return false;
    }

    private static int skipLeadingWhitespaceAndComments(String sql, int start) {
        int cursor = start;
        boolean consumedComment;
        do {
            consumedComment = false;
            while (cursor < sql.length() && Character.isWhitespace(sql.charAt(cursor))) {
                cursor++;
            }
            if (cursor + 1 < sql.length() && sql.charAt(cursor) == '-' && sql.charAt(cursor + 1) == '-') {
                consumedComment = true;
                cursor += 2;
                while (cursor < sql.length() && sql.charAt(cursor) != '\n') {
                    cursor++;
                }
            } else if (cursor < sql.length() && sql.charAt(cursor) == '#') {
                consumedComment = true;
                cursor++;
                while (cursor < sql.length() && sql.charAt(cursor) != '\n') {
                    cursor++;
                }
            } else if (cursor + 1 < sql.length() && sql.charAt(cursor) == '/' && sql.charAt(cursor + 1) == '*') {
                consumedComment = true;
                cursor += 2;
                while (cursor + 1 < sql.length() && !(sql.charAt(cursor) == '*' && sql.charAt(cursor + 1) == '/')) {
                    cursor++;
                }
                if (cursor + 1 >= sql.length()) {
                    return sql.length();
                }
                cursor += 2;
            }
        } while (consumedComment);
        return cursor;
    }

    private static boolean startsWithKeyword(String sql, int start, String keyword) {
        if (start < 0 || start + keyword.length() > sql.length()) {
            return false;
        }
        if (!sql.regionMatches(true, start, keyword, 0, keyword.length())) {
            return false;
        }
        int end = start + keyword.length();
        return end >= sql.length() || !Character.isLetterOrDigit(sql.charAt(end)) && sql.charAt(end) != '_';
    }

    private static boolean isBareDatabaseNameChar(char currentChar) {
        return currentChar == '_' || currentChar == '$' || currentChar == '.'
                || Character.isLetterOrDigit(currentChar);
    }

    public static final class ParsedBatch {
        private final String effectiveDbName;
        private final String sqlWithoutUse;
        private final String errorMessage;

        private ParsedBatch(String effectiveDbName, String sqlWithoutUse, String errorMessage) {
            this.effectiveDbName = effectiveDbName;
            this.sqlWithoutUse = sqlWithoutUse;
            this.errorMessage = errorMessage;
        }

        public String getEffectiveDbName() {
            return effectiveDbName;
        }

        public String getSqlWithoutUse() {
            return sqlWithoutUse;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isValid() {
            return errorMessage == null;
        }

        private static ParsedBatch success(String effectiveDbName, String sqlWithoutUse) {
            return new ParsedBatch(effectiveDbName, sqlWithoutUse, null);
        }

        private static ParsedBatch error(String errorMessage) {
            return new ParsedBatch(null, null, errorMessage);
        }
    }

    private static final class ParsedIdentifier {
        private final String databaseName;
        private final int endIndex;

        private ParsedIdentifier(String databaseName, int endIndex) {
            this.databaseName = databaseName;
            this.endIndex = endIndex;
        }
    }

    private static final class LeadingUseStatement {
        private final String databaseName;
        private final int endIndex;
        private final String errorMessage;

        private LeadingUseStatement(String databaseName, int endIndex, String errorMessage) {
            this.databaseName = databaseName;
            this.endIndex = endIndex;
            this.errorMessage = errorMessage;
        }

        private static LeadingUseStatement none() {
            return new LeadingUseStatement(null, 0, null);
        }

        private static LeadingUseStatement error(String errorMessage) {
            return new LeadingUseStatement(null, 0, errorMessage);
        }
    }
}
