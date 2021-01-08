package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 列实体
 * @author guohai
 * @date 2020-12-1
 */
@Data
@AllArgsConstructor
public class ColumnsNameBean {
    /**
     * 列名
     */
    private String columnName;
    /**
     * 列类型
     */
    private String columnType;
    /**
     * 列长度
     */
    private String columnLength;

}
