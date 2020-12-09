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

    private String indexName;

    private String indexDescription;

    private String indexKeys;
}
