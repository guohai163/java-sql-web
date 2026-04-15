package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Dashboard user ranking item.
 */
@Data
public class DashboardUserRankingItem {

    private Integer rank;

    private String userName;

    private Long queryCount;

    private Long totalReturnedRows;

    private Integer databaseCount;

    private Integer tableCount;

    private Double averageQueryConsuming;
}
