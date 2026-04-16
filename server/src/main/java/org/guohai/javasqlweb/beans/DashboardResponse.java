package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.List;

/**
 * Dashboard response payload.
 */
@Data
public class DashboardResponse {

    private String range;

    private String grain;

    private DashboardSummary summary;

    private PoolStatBean pool;

    private List<DashboardTrendPoint> trend;

    private List<DashboardUserRankingItem> userRanking;

    private List<DashboardObjectHotspotItem> databaseHotspots;

    private List<DashboardObjectHotspotItem> tableHotspots;

    private List<DashboardRecentQueryItem> recentQueries;
}
