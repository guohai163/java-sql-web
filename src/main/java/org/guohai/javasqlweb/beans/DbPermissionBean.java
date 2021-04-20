package org.guohai.javasqlweb.beans;

import lombok.Data;

/**
 * 数据库权限组
 * @author guohai
 */
@Data
public class DbPermissionBean {
    private Integer groupCode;
    private String groupName;
    private String serverList;
}
