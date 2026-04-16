package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Dashboard hotspot item.
 */
@Data
public class DashboardObjectHotspotItem {

    private String objectName;

    private String serverName;

    private String databaseName;

    private String tableName;

    private Long queryCount;

    private Long totalReturnedRows;
}
