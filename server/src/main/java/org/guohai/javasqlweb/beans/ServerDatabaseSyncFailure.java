package org.guohai.javasqlweb.beans;

import lombok.Data;

@Data
public class ServerDatabaseSyncFailure {
    private Integer serverCode;
    private String serverName;
    private String message;
}
