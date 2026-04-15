package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * 安全任务对外信息
 */
@Data
public class SecurityTaskInfo {

    private String userName;

    private String email;

    private UserSecurityTaskType taskType;

    private UserSecurityTaskStatus taskStatus;

    private Date expireTime;

    private String token;

    private String authSecret;
}
