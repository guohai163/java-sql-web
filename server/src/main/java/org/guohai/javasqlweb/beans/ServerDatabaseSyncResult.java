package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ServerDatabaseSyncResult {
    private Integer totalServers;
    private Integer successCount;
    private Integer failCount;
    private String syncedAt;
    private List<ServerDatabaseSyncFailure> failures = new ArrayList<>();
}
