package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.List;

/**
 * Cursor-based query log response payload.
 */
@Data
public class QueryLogCursorResponse {

    private List<QueryLogBean> items;

    private Integer pageSize;

    private Integer firstCode;

    private Integer lastCode;

    private Boolean hasOlder;

    private Boolean hasNewer;
}
