package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * k8s 探针服务
 */
@Service
public class ProbeService {

    private static final Logger LOG = LoggerFactory.getLogger(ProbeService.class);

    private final DataSource dataSource;

    public ProbeService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Result<String> checkLiveness() {
        return new Result<>(true, "", "alive");
    }

    public Result<String> checkReadiness() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return new Result<>(true, "", "ready");
            }
            String message = "主数据库连接校验未通过";
            LOG.warn("Readiness check failed: {}", message);
            return new Result<>(false, message, message);
        } catch (SQLException exception) {
            String message = exception.getMessage() == null ? "主数据库连接失败" : exception.getMessage();
            LOG.warn("Readiness check failed: {}", message);
            return new Result<>(false, message, message);
        }
    }
}
