package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * Dashboard recent query row.
 */
@Data
public class DashboardRecentQueryItem {

    private Date queryTime;

    private String queryName;

    private String serverName;

    private String queryDatabase;

    private String targetTables;

    private Integer resultRowCount;

    private Integer queryConsuming;

    private String querySqlscript;
}
