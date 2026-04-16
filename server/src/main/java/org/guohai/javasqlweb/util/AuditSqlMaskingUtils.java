package org.guohai.javasqlweb.util;

/**
 * Basic SQL masking for audit logs.
 */
public final class AuditSqlMaskingUtils {

    private AuditSqlMaskingUtils() {
    }

    public static String mask(String sql) {
        if (sql == null) {
            return "";
        }
        return sql
                .replaceAll("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("(?<!\\d)1\\d{10}(?!\\d)", "***********")
                .replaceAll("(?<!\\d)\\d{12,}(?!\\d)", "************");
    }
}
