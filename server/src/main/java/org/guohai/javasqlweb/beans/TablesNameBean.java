package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 表实体
 * @author guohai
 * @date 2020-12-1
 */
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
    private Long tableRows;
}
