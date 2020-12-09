package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ColumnsNameBean {

    private String columnName;

    private String columnType;

    private String columnLength;

}
