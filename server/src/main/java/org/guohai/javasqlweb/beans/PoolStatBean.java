package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Connection pool summary bean.
 */
@Data
public class PoolStatBean {
    private String poolName;
    private String jdbcUrl;
    private String driverClassName;
    private Integer activeConnections;
    private Integer idleConnections;
    private Integer totalConnections;
    private Integer threadsAwaitingConnection;
    private String healthStatus;
}
