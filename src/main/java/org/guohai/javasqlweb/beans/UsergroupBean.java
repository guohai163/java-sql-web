package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * 用户组实体类
 * @author guohai
 * @date 2021-4-1
 */
@Data
public class UsergroupBean {

    /**
     * 组编号
     */
    private Integer code;

    /**
     * 组名
     */
    private String groupName;

    /**
     * 备注
     */
    private String comment;

    /**
     * 用户明细
     */
    private String userArray;
}
