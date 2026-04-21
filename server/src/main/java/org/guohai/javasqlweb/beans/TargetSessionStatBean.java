package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * Runtime detail row for an active target database session.
 */
@Data
public class TargetSessionStatBean {

    private Integer serverCode;

    private String dbType;

    private String sessionId;

    private String platformUserName;

    private String databaseUserName;

    private String clientHost;

    private String databaseName;

    private String sessionStatus;

    private String commandOrWait;

    private Long runningSeconds;

    private Date queryStartTime;

    private String sqlText;

    private Integer queryLogCode;

    private Boolean matchedByPlatformTrace;
}
