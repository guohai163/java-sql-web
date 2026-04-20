package org.guohai.javasqlweb.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only SQL guard that preserves multi-statement execution but validates every statement.
 */
public final class ReadOnlySqlGuard {

    private static final String READ_ONLY_ERROR = "仅允许只读查询；多语句中包含不允许的子语句";

    private ReadOnlySqlGuard() {
    }

    public static String validate(String sql, String dbType) {
        for (String statement : splitStatements(sql)) {
            String normalized = stripStringsAndComments(statement);
            String trimmed = normalized.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isAllowedVariableStatement(trimmed, dbType)) {
                continue;
            }

            String firstKeyword = extractFirstKeyword(trimmed);
            if (isAllowedReadOnlyStatement(trimmed, firstKeyword)) {
                continue;
            }
            return READ_ONLY_ERROR;
        }
        return null;
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        if (sql == null || sql.isEmpty()) {
            return statements;
        }
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inBracket = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char currentChar = sql.charAt(i);
            char nextChar = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(currentChar);
                if (currentChar == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                current.append(currentChar);
                if (currentChar == '*' && nextChar == '/') {
                    current.append(nextChar);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && !inBracket) {
                if (currentChar == '-' && nextChar == '-') {
                    current.append(currentChar).append(nextChar);
                    i++;
                    inLineComment = true;
                    continue;
                }
                if (currentChar == '#') {
                    current.append(currentChar);
                    inLineComment = true;
                    continue;
                }
                if (currentChar == '/' && nextChar == '*') {
                    current.append(currentChar).append(nextChar);
                    i++;
                    inBlockComment = true;
                    continue;
                }
            }

            if (!inDouble && !inBacktick && !inBracket && currentChar == '\'') {
                current.append(currentChar);
                if (inSingle && nextChar == '\'') {
                    current.append(nextChar);
                    i++;
                } else {
                    inSingle = !inSingle;
                }
                continue;
            }
            if (!inSingle && !inBacktick && !inBracket && currentChar == '"') {
                current.append(currentChar);
                if (inDouble && nextChar == '"') {
                    current.append(nextChar);
                    i++;
                } else {
                    inDouble = !inDouble;
                }
                continue;
            }
            if (!inSingle && !inDouble && !inBracket && currentChar == '`') {
                current.append(currentChar);
                inBacktick = !inBacktick;
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && currentChar == '[') {
                inBracket = true;
                current.append(currentChar);
                continue;
            }
            if (inBracket && currentChar == ']') {
                inBracket = false;
                current.append(currentChar);
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && !inBracket && currentChar == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }

        if (current.length() > 0) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static boolean isAllowedVariableStatement(String sql, String dbType) {
        String upperDbType = dbType == null ? "" : dbType.toLowerCase(Locale.ROOT);
        String normalized = sql.toUpperCase(Locale.ROOT);
        if ("mysql".equals(upperDbType) || "mariadb".equals(upperDbType)) {
            return normalized.matches("^SET\\s+@[_A-Z0-9$]+\\s*(:=|=).*$");
        }
        if ("mssql".equals(upperDbType)) {
            return isAllowedMssqlDeclareStatement(sql)
                    || normalized.matches("^SET\\s+@[_A-Z0-9]+\\s*=.*$");
        }
        return false;
    }

    private static boolean isAllowedMssqlDeclareStatement(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        if (!trimmed.regionMatches(true, 0, "DECLARE", 0, "DECLARE".length())) {
            return false;
        }
        String body = trimmed.substring("DECLARE".length()).trim();
        if (body.isEmpty()) {
            return false;
        }
        for (String declaration : splitTopLevelCommaSegments(body)) {
            if (!isAllowedMssqlScalarDeclaration(declaration)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitTopLevelCommaSegments(String sql) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char currentChar = sql.charAt(i);
            if (currentChar == '(') {
                depth++;
            } else if (currentChar == ')') {
                if (depth > 0) {
                    depth--;
                }
            } else if (currentChar == ',' && depth == 0) {
                segments.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments;
    }

    private static boolean isAllowedMssqlScalarDeclaration(String declaration) {
        if (declaration == null || declaration.isBlank()) {
            return false;
        }
        int index = 0;
        if (declaration.charAt(index) != '@') {
            return false;
        }
        index++;
        while (index < declaration.length() && isMssqlVariableChar(declaration.charAt(index))) {
            index++;
        }
        if (index == 1) {
            return false;
        }
        while (index < declaration.length() && Character.isWhitespace(declaration.charAt(index))) {
            index++;
        }
        if (index >= declaration.length()) {
            return false;
        }

        int typeStart = index;
        int depth = 0;
        while (index < declaration.length()) {
            char currentChar = declaration.charAt(index);
            if (currentChar == '(') {
                depth++;
            } else if (currentChar == ')') {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (currentChar == '=' && depth == 0) {
                break;
            }
            index++;
        }
        if (depth != 0) {
            return false;
        }

        String typeClause = declaration.substring(typeStart, index).trim();
        if (typeClause.isEmpty()) {
            return false;
        }
        String normalizedType = typeClause.toUpperCase(Locale.ROOT);
        if ("TABLE".equals(extractFirstKeyword(typeClause))
                || normalizedType.startsWith("AS TABLE")) {
            return false;
        }

        if (index < declaration.length()) {
            return !declaration.substring(index + 1).trim().isEmpty();
        }
        return true;
    }

    private static boolean isMssqlVariableChar(char currentChar) {
        return currentChar == '_' || Character.isLetterOrDigit(currentChar);
    }

    private static boolean isAllowedReadOnlyStatement(String sql, String firstKeyword) {
        if (firstKeyword.isEmpty()) {
            return true;
        }
        switch (firstKeyword) {
            case "SELECT":
            case "SHOW":
            case "DESCRIBE":
            case "DESC":
            case "EXPLAIN":
                return !containsDangerousReadOnlySideEffects(sql);
            case "WITH":
                return "SELECT".equals(resolveTopLevelCommandAfterWith(sql))
                        && !containsDangerousReadOnlySideEffects(sql);
            default:
                return false;
        }
    }

    private static boolean containsDangerousReadOnlySideEffects(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        return upper.contains(" INTO ")
                || upper.contains("INTO OUTFILE")
                || upper.contains("INTO DUMPFILE")
                || upper.contains("COPY ");
    }

    private static String resolveTopLevelCommandAfterWith(String sql) {
        int index = 4;
        int depth = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                if (depth > 0) {
                    depth--;
                }
            } else if (depth == 0 && Character.isLetter(current)) {
                int start = index;
                while (index < sql.length() && (Character.isLetter(sql.charAt(index)) || sql.charAt(index) == '_')) {
                    index++;
                }
                String token = sql.substring(start, index).toUpperCase(Locale.ROOT);
                if ("SELECT".equals(token) || "UPDATE".equals(token) || "DELETE".equals(token)
                        || "INSERT".equals(token) || "MERGE".equals(token)) {
                    return token;
                }
                continue;
            }
            index++;
        }
        return "";
    }

    static String extractFirstKeyword(String sql) {
        int index = 0;
        while (index < sql.length() && !Character.isLetter(sql.charAt(index))) {
            index++;
        }
        int start = index;
        while (index < sql.length() && (Character.isLetter(sql.charAt(index)) || sql.charAt(index) == '_')) {
            index++;
        }
        return start < index ? sql.substring(start, index).toUpperCase(Locale.ROOT) : "";
    }

    static String stripStringsAndComments(String sql) {
        StringBuilder sanitized = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inBracket = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char currentChar = sql.charAt(i);
            char nextChar = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (currentChar == '\n') {
                    inLineComment = false;
                    sanitized.append('\n');
                } else {
                    sanitized.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    sanitized.append("  ");
                    i++;
                    inBlockComment = false;
                } else {
                    sanitized.append(currentChar == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && !inBracket) {
                if (currentChar == '-' && nextChar == '-') {
                    sanitized.append("  ");
                    i++;
                    inLineComment = true;
                    continue;
                }
                if (currentChar == '#') {
                    sanitized.append(' ');
                    inLineComment = true;
                    continue;
                }
                if (currentChar == '/' && nextChar == '*') {
                    sanitized.append("  ");
                    i++;
                    inBlockComment = true;
                    continue;
                }
            }

            if (!inDouble && !inBacktick && !inBracket && currentChar == '\'') {
                sanitized.append(' ');
                if (inSingle && nextChar == '\'') {
                    sanitized.append(' ');
                    i++;
                } else {
                    inSingle = !inSingle;
                }
                continue;
            }
            if (!inSingle && !inBacktick && !inBracket && currentChar == '"') {
                sanitized.append(' ');
                if (inDouble && nextChar == '"') {
                    sanitized.append(' ');
                    i++;
                } else {
                    inDouble = !inDouble;
                }
                continue;
            }
            if (!inSingle && !inDouble && !inBracket && currentChar == '`') {
                sanitized.append(' ');
                inBacktick = !inBacktick;
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && currentChar == '[') {
                sanitized.append(' ');
                inBracket = true;
                continue;
            }
            if (inBracket && currentChar == ']') {
                sanitized.append(' ');
                inBracket = false;
                continue;
            }

            if (inSingle || inDouble || inBacktick || inBracket) {
                sanitized.append(currentChar == '\n' ? '\n' : ' ');
            } else {
                sanitized.append(currentChar);
            }
        }
        return sanitized.toString();
    }
}
