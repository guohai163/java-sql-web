package org.guohai.javasqlweb.service.operation;

import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 用户查询资源控制与返回计数工具。
 */
final class QueryExecutionUtils {

    private static final int DEFAULT_FETCH_SIZE = 1000;

    private QueryExecutionUtils() {
    }

    static void applyQueryControls(Statement statement, int queryTimeoutSeconds, Integer limit) throws SQLException {
        if (queryTimeoutSeconds > 0) {
            statement.setQueryTimeout(queryTimeoutSeconds);
        }
        int fetchLimit = fetchLimit(limit);
        if (fetchLimit > 0) {
            statement.setMaxRows(fetchLimit);
        }
        try {
            statement.setFetchSize(resolveFetchSize(limit));
        } catch (SQLFeatureNotSupportedException ignored) {
            // 部分 JDBC 驱动不支持 fetchSize；此时保留 timeout 与 maxRows 保护即可。
        }
    }

    static boolean shouldStopBeforeAdding(List<?> rows, Integer limit) {
        return rows.size() >= safeLimit(limit);
    }

    static void fillResult(Object[] result, List<?> rows, boolean hasMore) {
        result[0] = hasMore ? rows.size() + 1 : rows.size();
        result[1] = rows.size();
        result[2] = rows;
    }

    static String ensureTrailingSemicolon(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.endsWith(";")) {
            return normalized;
        }
        return normalized + ";";
    }

    private static int safeLimit(Integer limit) {
        if (limit == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, limit);
    }

    private static int fetchLimit(Integer limit) {
        int safeLimit = safeLimit(limit);
        if (safeLimit == Integer.MAX_VALUE) {
            return 0;
        }
        return safeLimit + 1;
    }

    private static int resolveFetchSize(Integer limit) {
        int fetchLimit = fetchLimit(limit);
        if (fetchLimit <= 0) {
            return DEFAULT_FETCH_SIZE;
        }
        return Math.min(fetchLimit, DEFAULT_FETCH_SIZE);
    }
}
