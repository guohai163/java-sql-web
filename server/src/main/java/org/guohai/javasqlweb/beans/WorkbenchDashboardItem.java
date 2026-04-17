package org.guohai.javasqlweb.beans;

import lombok.Data;

@Data
public class WorkbenchDashboardItem {
    private String key;
    private String label;
    private String value;
    private String unit;
    private String status;
    private String message;
}
