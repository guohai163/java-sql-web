package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.ServerDatabaseSyncResult;
import org.guohai.javasqlweb.beans.TargetPoolStatBean;
import org.guohai.javasqlweb.beans.TargetSessionStatBean;
import org.guohai.javasqlweb.service.BackstageService;
import org.guohai.javasqlweb.service.PermissionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BackstageControllerTests {

    @Mock
    private BackstageService backstageService;

    @Mock
    private PermissionsService permissionsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BackstageController controller = new BackstageController();
        ReflectionTestUtils.setField(controller, "backstageService", backstageService);
        ReflectionTestUtils.setField(controller, "permissionsService", permissionsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void serverRuntimeShouldReturnSnapshotList() throws Exception {
        TargetPoolStatBean stat = new TargetPoolStatBean();
        stat.setServerCode(9);
        stat.setRuntimeStatus("warning");
        when(backstageService.getTargetPoolStats()).thenReturn(new Result<>(true, "", List.of(stat)));

        mockMvc.perform(get("/api/backstage/server-runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].serverCode").value(9))
                .andExpect(jsonPath("$.data[0].runtimeStatus").value("warning"));
    }

    @Test
    void connListShouldSupportKeywordAndDbNameFilter() throws Exception {
        ConnectConfigBean server = new ConnectConfigBean();
        server.setCode(8);
        server.setDbServerName("core");
        when(backstageService.getConnData("order_db", "mysql", "order_db"))
                .thenReturn(new Result<>(true, "", List.of(server)));

        mockMvc.perform(get("/api/backstage/connlist")
                        .param("keyword", "order_db")
                        .param("serverType", "mysql")
                        .param("dbName", "order_db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].code").value(8))
                .andExpect(jsonPath("$.data[0].dbServerName").value("core"));

        verify(backstageService).getConnData("order_db", "mysql", "order_db");
    }

    @Test
    void syncServerDatabasesShouldReturnSummary() throws Exception {
        ServerDatabaseSyncResult result = new ServerDatabaseSyncResult();
        result.setTotalServers(2);
        result.setSuccessCount(1);
        result.setFailCount(1);
        result.setSyncedAt("2026-04-28 12:00:00");
        when(backstageService.syncServerDatabases()).thenReturn(new Result<>(true, "", result));

        mockMvc.perform(post("/api/backstage/server-databases/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.totalServers").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.syncedAt").value("2026-04-28 12:00:00"));
    }

    @Test
    void resetServerShouldReturnSuccessMessage() throws Exception {
        when(backstageService.resetServer(9)).thenReturn(new Result<>(true, "", "已重置目标库连接池并清除冷却状态"));

        mockMvc.perform(post("/api/backstage/resetserver/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data").value("已重置目标库连接池并清除冷却状态"));
    }

    @Test
    void serverRuntimeSessionsShouldReturnSessionList() throws Exception {
        TargetSessionStatBean session = new TargetSessionStatBean();
        session.setServerCode(9);
        session.setSessionId("501");
        session.setPlatformUserName("alice");
        when(backstageService.getTargetPoolSessions(9)).thenReturn(new Result<>(true, "", List.of(session)));

        mockMvc.perform(get("/api/backstage/server-runtime/9/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].serverCode").value(9))
                .andExpect(jsonPath("$.data[0].sessionId").value("501"))
                .andExpect(jsonPath("$.data[0].platformUserName").value("alice"));
    }
}
