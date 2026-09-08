package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vanna 使用的历史查询样本。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VannaHistoryExample {
    private Integer queryLogCode;
    private String queryName;
    private String queryDatabase;
    private String sqlTemplate;
    private String targetTables;
    private String queryTime;
}
