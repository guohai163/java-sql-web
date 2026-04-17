package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchDashboardSection {
    private String key;
    private String title;
    private String status;
    private List<WorkbenchDashboardItem> items = new ArrayList<>();
}
