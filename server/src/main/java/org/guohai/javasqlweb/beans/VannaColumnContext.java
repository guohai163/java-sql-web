package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vanna 使用的列级上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VannaColumnContext {
    private String tableName;
    private String columnName;
    private String columnType;
    private String columnLength;
    private String columnComment;
    private String columnIsNull;
}
