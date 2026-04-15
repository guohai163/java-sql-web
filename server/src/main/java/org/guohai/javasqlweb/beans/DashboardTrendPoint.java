package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Trend chart point.
 */
@Data
public class DashboardTrendPoint {

    private String timeBucket;

    private Long queryCount;

    private Long totalReturnedRows;
}
