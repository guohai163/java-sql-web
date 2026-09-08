package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 供 Vanna 服务消费的上下文响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VannaContextResponse {
    private String serverType;
    private String dialect;
    private String serverName;
    private String dbName;
    private String contextVersion;
    private List<TablesNameBean> tables = new ArrayList<>();
    private List<VannaColumnContext> columns = new ArrayList<>();
    private List<ViewNameBean> views = new ArrayList<>();
    private List<VannaHistoryExample> historyExamples = new ArrayList<>();
}
