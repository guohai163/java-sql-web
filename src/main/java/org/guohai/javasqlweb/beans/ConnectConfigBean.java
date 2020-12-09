package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

@Data
public class ConnectConfigBean {

    private Integer code;

    private String dbServerName;

    private String dbServerHost;

    private String dbServerPort;

    private String dbServerUsername;

    private String dbServerPassword;

    private String dbServerType;

    private Date createTime;

}
