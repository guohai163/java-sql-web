package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * 数据服务器连接实体类
 * @author guohai
 * @date 2021-1-1
 */
@Data
public class ConnectConfigBean {
    /**
     * 编号
     */
    private Integer code;
    /**
     * 服务器机器名
     */
    private String dbServerName;
    /**
     * 服务器地址
     */
    private String dbServerHost;
    /**
     * 服务器端口
     */
    private String dbServerPort;
    /**
     * 服务器用户名
     */
    private String dbServerUsername;
    /**
     * 服务器密码
     */
    private String dbServerPassword;
    /**
     * 服务器类型，目前仅支持Mysql\sqlserver
     */
    private String dbServerType;
    /**
     * 创建时间
     */
    private Date createTime;

}
