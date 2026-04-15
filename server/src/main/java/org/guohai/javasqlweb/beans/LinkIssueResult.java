package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * 链接签发结果
 */
@Data
public class LinkIssueResult {

    private String userName;

    private String email;

    private UserSecurityTaskType taskType;

    private String linkUrl;

    private Date expireTime;
}
