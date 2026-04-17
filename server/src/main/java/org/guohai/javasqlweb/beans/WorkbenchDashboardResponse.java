package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchDashboardResponse {
    private Integer serverCode;
    private String dbName;
    private String dbType;
    private Long cachedAt;
    private Long expiresAt;
    private List<WorkbenchDashboardSection> sections = new ArrayList<>();
}
