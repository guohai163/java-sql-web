package org.guohai.javasqladmin.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储过程实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredProceduresBean {

    /**
     * 过程名
     */
    private String procedureName;

    /**
     * 过程内容
     */
    private String procedureData;

    /**
     * 单过程名的构造
     * @param name
     */
    public StoredProceduresBean(String name) {
        procedureName = name;
    }
}
