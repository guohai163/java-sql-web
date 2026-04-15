package org.guohai.javasqlweb.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

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
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(20);
        config.setConnectionTimeout(10000L);
        config.setValidationTimeout(5000L);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTestQuery(validationQuery);
        return new HikariDataSource(config);
    }
}
