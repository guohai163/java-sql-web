package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Dashboard summary cards.
 */
@Data
public class DashboardSummary {

    private Integer totalUsers;

    private Integer newUsers;

    private Integer totalInstances;

    private Integer healthyInstances;

    private Integer totalPoolConnections;

    private Integer activePoolConnections;

    private Integer idlePoolConnections;

    private Integer waitingPoolThreads;

    private Long queryCount;

    private Long totalReturnedRows;

    private Double averageQueryConsuming;
}
