package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * Query execution result with database session metadata.
 */
@Data
public class QueryExecutionResult {

    private String dbSessionId;

    private Object[] rows;
}
