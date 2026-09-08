package org.guohai.javasqlweb.util;

/**
 * SQL 标识符转义工具，仅用于无法通过 PreparedStatement 参数化的位置。
 */
public final class SqlIdentifierUtils {

    private SqlIdentifierUtils() {
    }

    public static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("SQL identifier must not be null");
        }
        String normalized = identifier.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("SQL identifier must not be empty");
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                throw new IllegalArgumentException("SQL identifier contains control characters");
            }
        }
        return normalized;
    }

    public static String quoteMysqlIdentifier(String identifier) {
        return "`" + normalizeIdentifier(identifier).replace("`", "``") + "`";
    }

    public static String quoteMssqlIdentifier(String identifier) {
        return "[" + normalizeIdentifier(identifier).replace("]", "]]") + "]";
    }

    public static String quotePostgresqlIdentifier(String identifier) {
        return "\"" + normalizeIdentifier(identifier).replace("\"", "\"\"") + "\"";
    }
}
