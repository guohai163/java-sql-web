package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Runtime snapshot for a dynamic target database pool.
 */
@Data
public class TargetPoolStatBean {

    private Integer serverCode;

    private String serverName;

    private String dbType;

    private String poolName;

    private Integer activeConnections;

    private Integer idleConnections;

    private Integer totalConnections;

    private Integer threadsAwaitingConnection;

    private Integer failureCount;

    private Boolean inCooldown;

    private Long cooldownRemainingSeconds;

    private String lastError;

    private String runtimeStatus;
}
