package org.guohai.javasqlweb.util;

import org.guohai.javasqlweb.beans.QueryLogTargetBean;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract table targets from SQL.
 */
public final class SqlTargetExtractor {

    private static final Pattern CTE_PATTERN = Pattern.compile("(?i)(?:^|,)\\s*([a-zA-Z_][\\w$]*)\\s+AS\\s*\\(");
    private static final Set<String> JOIN_KEYWORDS = new HashSet<>(Arrays.asList(
            "FROM", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "OUTER"
    ));
    private static final Set<String> COMMON_SCHEMA_NAMES = new HashSet<>(Arrays.asList(
            "dbo", "public", "sys", "pg_catalog", "information_schema"
    ));

    private SqlTargetExtractor() {
    }

    public static List<QueryLogTargetBean> extract(String sql, String defaultDatabase) {
        if (sql == null || sql.trim().isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, QueryLogTargetBean> targets = new LinkedHashMap<>();
        for (String statement : ReadOnlySqlGuard.splitStatements(sql)) {
            String sanitized = stripStringsAndComments(statement);
            if (sanitized.trim().isEmpty()) {
                continue;
            }
            Set<String> cteNames = extractCteNames(sanitized);
            scanTargets(sanitized, defaultDatabase, cteNames, targets);
        }
        return new ArrayList<>(targets.values());
    }

    private static void scanTargets(String sql,
                                    String defaultDatabase,
                                    Set<String> cteNames,
                                    LinkedHashMap<String, QueryLogTargetBean> targets) {
        int index = 0;
        while (index < sql.length()) {
            if (!Character.isLetter(sql.charAt(index))) {
                index++;
                continue;
            }
            int wordEnd = index;
            while (wordEnd < sql.length()
                    && (Character.isLetter(sql.charAt(wordEnd)) || sql.charAt(wordEnd) == '_')) {
                wordEnd++;
            }
            String keyword = sql.substring(index, wordEnd).toUpperCase(Locale.ROOT);
            if (!JOIN_KEYWORDS.contains(keyword)) {
                index = wordEnd;
                continue;
            }

            if (!"FROM".equals(keyword) && !"JOIN".equals(keyword)) {
                index = wordEnd;
                continue;
            }

            int targetStart = skipWhitespace(sql, wordEnd);
            if (targetStart >= sql.length()) {
                break;
            }
            if (sql.charAt(targetStart) == '(') {
                index = targetStart + 1;
                continue;
            }

            String identifier = readIdentifier(sql, targetStart);
            if (identifier.isEmpty()) {
                index = targetStart + 1;
                continue;
            }
            String normalizedIdentifier = normalizeIdentifier(identifier);
            if (normalizedIdentifier.isEmpty()) {
                index = targetStart + identifier.length();
                continue;
            }
            String bareName = normalizedIdentifier.contains(".")
                    ? normalizedIdentifier.substring(normalizedIdentifier.lastIndexOf('.') + 1)
                    : normalizedIdentifier;
            if (cteNames.contains(bareName.toLowerCase(Locale.ROOT))) {
                index = targetStart + identifier.length();
                continue;
            }

            QueryLogTargetBean target = buildTarget(normalizedIdentifier, defaultDatabase);
            if (target != null && target.getTableName() != null && !target.getTableName().trim().isEmpty()) {
                String key = (target.getDatabaseName() == null ? "" : target.getDatabaseName().toLowerCase(Locale.ROOT))
                        + "|"
                        + target.getTableName().toLowerCase(Locale.ROOT);
                targets.putIfAbsent(key, target);
            }
            index = targetStart + identifier.length();
        }
    }

    private static Set<String> extractCteNames(String sql) {
        Set<String> cteNames = new HashSet<>();
        if (!sql.trim().toUpperCase(Locale.ROOT).startsWith("WITH ")) {
            return cteNames;
        }
        Matcher matcher = CTE_PATTERN.matcher(sql);
        while (matcher.find()) {
            cteNames.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return cteNames;
    }

    private static QueryLogTargetBean buildTarget(String identifier, String defaultDatabase) {
        String[] rawSegments = identifier.split("\\.");
        List<String> segments = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            String value = rawSegment == null ? "" : rawSegment.trim();
            if (!value.isEmpty()) {
                segments.add(value);
            }
        }
        if (segments.isEmpty()) {
            return null;
        }

        QueryLogTargetBean target = new QueryLogTargetBean();
        if (segments.size() == 1) {
            target.setDatabaseName(defaultDatabase);
            target.setTableName(segments.get(0));
            return target;
        }
        if (segments.size() == 2) {
            if (defaultDatabase != null && defaultDatabase.equalsIgnoreCase(segments.get(0))) {
                target.setDatabaseName(segments.get(0));
                target.setTableName(segments.get(1));
            } else if (COMMON_SCHEMA_NAMES.contains(segments.get(0).toLowerCase(Locale.ROOT))) {
                target.setDatabaseName(defaultDatabase);
                target.setTableName(segments.get(0) + "." + segments.get(1));
            } else {
                target.setDatabaseName(segments.get(0));
                target.setTableName(segments.get(1));
            }
            return target;
        }
        target.setDatabaseName(segments.get(segments.size() - 3));
        target.setTableName(segments.get(segments.size() - 2) + "." + segments.get(segments.size() - 1));
        return target;
    }

    private static int skipWhitespace(String sql, int index) {
        int cursor = index;
        while (cursor < sql.length() && Character.isWhitespace(sql.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static String readIdentifier(String sql, int start) {
        StringBuilder builder = new StringBuilder();
        int index = start;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (Character.isWhitespace(current) || current == ',' || current == ')' || current == ';') {
                break;
            }
            builder.append(current);
            if (current == '`') {
                index++;
                while (index < sql.length()) {
                    char quoted = sql.charAt(index);
                    builder.append(quoted);
                    if (quoted == '`') {
                        break;
                    }
                    index++;
                }
            } else if (current == '[') {
                index++;
                while (index < sql.length()) {
                    char quoted = sql.charAt(index);
                    builder.append(quoted);
                    if (quoted == ']') {
                        break;
                    }
                    index++;
                }
            } else if (current == '"') {
                index++;
                while (index < sql.length()) {
                    char quoted = sql.charAt(index);
                    builder.append(quoted);
                    if (quoted == '"') {
                        break;
                    }
                    index++;
                }
            }
            index++;
        }
        return builder.toString();
    }

    private static String normalizeIdentifier(String identifier) {
        String[] rawSegments = identifier.split("\\.");
        List<String> normalizedSegments = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            String trimmed = rawSegment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ((trimmed.startsWith("`") && trimmed.endsWith("`"))
                    || (trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            normalizedSegments.add(trimmed);
        }
        return String.join(".", normalizedSegments);
    }

    private static String stripStringsAndComments(String sql) {
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
                sanitized.append(currentChar);
                if (inDouble && nextChar == '"') {
                    sanitized.append(nextChar);
                    i++;
                } else {
                    inDouble = !inDouble;
                }
                continue;
            }
            if (!inSingle && !inDouble && !inBracket && currentChar == '`') {
                sanitized.append(currentChar);
                inBacktick = !inBacktick;
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && currentChar == '[') {
                sanitized.append(currentChar);
                inBracket = true;
                continue;
            }
            if (inBracket && currentChar == ']') {
                sanitized.append(currentChar);
                inBracket = false;
                continue;
            }
            if (inSingle) {
                sanitized.append(' ');
                continue;
            }
            sanitized.append(currentChar);
        }
        return sanitized.toString();
    }
}
