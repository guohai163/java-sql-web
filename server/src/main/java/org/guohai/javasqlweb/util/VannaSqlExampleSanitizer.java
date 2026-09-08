package org.guohai.javasqlweb.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 对历史 SQL 做脱敏和模板化，避免把敏感字面量送入 Vanna 的 prompt / embedding。
 */
public final class VannaSqlExampleSanitizer {

    private static final Pattern SINGLE_QUOTED = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"(?:\"\"|[^\"])*\"");
    private static final Pattern DATE_LITERAL = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2}:\\d{2})?\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![A-Za-z_])\\d{4,}(?![A-Za-z_])");
    private static final Pattern SHORT_NUMBER = Pattern.compile("(?<![A-Za-z_])\\d+(?:\\.\\d+)?(?![A-Za-z_])");
    private static final Pattern LONG_IN_LIST = Pattern.compile("(?is)\\bIN\\s*\\((?:\\s*[^,()]+\\s*,){3,}\\s*[^,()]+\\s*\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private VannaSqlExampleSanitizer() {
    }

    public static String sanitize(String sql) {
        if (sql == null) {
            return "";
        }
        String normalized = sql.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        normalized = SINGLE_QUOTED.matcher(normalized).replaceAll("'?'");
        normalized = DOUBLE_QUOTED.matcher(normalized).replaceAll("\"?\"");
        normalized = DATE_LITERAL.matcher(normalized).replaceAll("?");
        normalized = LONG_IN_LIST.matcher(normalized).replaceAll("IN (?, ?, ?, ...)");
        normalized = LONG_NUMBER.matcher(normalized).replaceAll("?");
        normalized = SHORT_NUMBER.matcher(normalized).replaceAll("?");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        return normalized;
    }

    public static boolean looksReadableSelect(String sql, String dbType) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        return ReadOnlySqlGuard.validate(sql, dbType) == null;
    }

    public static String dedupeKey(String sql) {
        return sanitize(sql).toLowerCase(Locale.ROOT);
    }
}
