package org.guohai.javasqladmin.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TablesNameBean {
    /**
     * 表名
     */
    private String tableName;

    /**
     * 表大小
     */
    private Integer tableRows;
}
