package org.guohai.javasqlweb.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;

/**
 * 用户安全任务
 */
@Data
public class UserSecurityTaskBean {

    private Integer code;

    @JsonIgnore
    private String taskUuidHash;

    private Integer userCode;

    private UserSecurityTaskType taskType;

    private UserSecurityTaskStatus taskStatus;

    private Date expireTime;

    private Date usedTime;

    private String createdBy;

    private Date createdTime;

    private String userName;

    private String email;

    private AccountStatus accountStatus;

    private OtpAuthStatus authStatus;

    private UserLoginStatus loginStatus;

    private String authSecret;

    private String token;
}
