package org.guohai.javasqlweb.config;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Configures the SQL dialect used by the application's own metadata database.
 * Target databases opened from the workbench keep their independent drivers and SQL implementations.
 */
@Configuration
public class AppDatabaseConfig {

    private final String appDatabaseDialect;

    public AppDatabaseConfig(@Value("${app.db.dialect:mysql}") String appDatabaseDialect) {
        this.appDatabaseDialect = normalizeDialect(appDatabaseDialect);
    }

    @Bean
    public ConfigurationCustomizer appDatabaseIdCustomizer() {
        return configuration -> configuration.setDatabaseId(appDatabaseDialect);
    }

    private String normalizeDialect(String dialect) {
        if (dialect == null || dialect.isBlank()) {
            return "mysql";
        }
        String normalized = dialect.trim().toLowerCase(Locale.ROOT);
        if ("postgres".equals(normalized) || "pgsql".equals(normalized)) {
            return "postgresql";
        }
        if ("mariadb".equals(normalized)) {
            return "mysql";
        }
        return "postgresql".equals(normalized) ? "postgresql" : "mysql";
    }
}
