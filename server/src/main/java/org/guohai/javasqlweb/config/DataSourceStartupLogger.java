package org.guohai.javasqlweb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Print the platform datasource configuration after the application is ready.
 * Sensitive values are masked before they are written to logs.
 */
@Component
public class DataSourceStartupLogger {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceStartupLogger.class);

    @Value("${spring.datasource.url}")
    private String dataSourceUrl;

    @Value("${spring.datasource.username:}")
    private String dataSourceUsername;

    @EventListener(ApplicationReadyEvent.class)
    public void logDataSourceInfo() {
        LOG.info("Platform datasource ready. url={}, username={}",
                sanitizeJdbcUrl(dataSourceUrl),
                maskValue(dataSourceUsername));
    }

    private String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        return jdbcUrl.replaceAll("(?i)(password=)[^&;]+", "$1******");
    }

    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 2) {
            return "**";
        }
        return value.charAt(0) + "******" + value.charAt(value.length() - 1);
    }
}
