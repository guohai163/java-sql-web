package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 表索引实体
 * @author guohai
 */
@Data
@AllArgsConstructor
public class TableIndexesBean {

    /**
     * 索引名
     */
    private String indexName;

    /**
     * 索引描述
     */
    private String indexDescription;

    /**
     * 索引KEY
     */
    private String indexKeys;
}
