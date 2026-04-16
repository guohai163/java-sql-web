package org.guohai.javasqlweb.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Hikari datasource helper for dynamic target database connections.
 */
public final class HikariDataSourceUtils {

    private HikariDataSourceUtils() {
    }

    public static DataSource createDataSource(String poolName,
                                              String jdbcUrl,
                                              String username,
                                              String password,
                                              String validationQuery) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(20);
        config.setConnectionTimeout(10000L);
        config.setValidationTimeout(5000L);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTestQuery(validationQuery);
        return new HikariDataSource(config);
    }

    public static void closeDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
            return;
        }
        if (dataSource instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
                return;
            } catch (Exception ignored) {
                return;
            }
        }
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                dataSource.unwrap(HikariDataSource.class).close();
            }
        } catch (SQLException ignored) {
            // Best-effort shutdown for dynamically created target pools.
        }
    }
}
