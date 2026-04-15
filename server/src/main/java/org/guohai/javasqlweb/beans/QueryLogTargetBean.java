package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Query log target table mapping.
 */
@Data
public class QueryLogTargetBean {

    private Integer code;

    private Integer queryLogCode;

    private String databaseName;

    private String tableName;
}
