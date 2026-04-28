package org.guohai.javasqlweb.service;

import com.zaxxer.hikari.HikariDataSource;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.DashboardDao;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

import javax.sql.DataSource;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackstageServiceImplTests {

    @Mock
    private BaseConfigDao baseConfigDao;

    @Mock
    private UserManageDao userManageDao;

    @Mock
    private DashboardDao dashboardDao;

    @Mock
    private DataSource dataSource;

    @Mock
    private HealthEndpoint healthEndpoint;

    @Mock
    private UserSecurityTaskService userSecurityTaskService;

    @Mock
    private BaseDataService baseDataService;

    @Spy
    @InjectMocks
    private BackstageServiceImpl backstageService;

    @Test
    void getDashboardBuildsSummaryAndNormalizesDayGrainForSevenDays() throws Exception {
        HikariDataSource hikariDataSource = org.mockito.Mockito.mock(HikariDataSource.class);
        DashboardSummary userSummary = new DashboardSummary();
        userSummary.setTotalUsers(8);
        userSummary.setNewUsers(2);
        DashboardTrendPoint trendPoint = new DashboardTrendPoint();
        trendPoint.setTimeBucket("2026-04-16");
        trendPoint.setQueryCount(5L);
        trendPoint.setTotalReturnedRows(50L);

        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikariDataSource);
        when(healthEndpoint.health()).thenReturn(Health.up().build());
        when(hikariDataSource.getPoolName()).thenReturn("core");
        when(hikariDataSource.getJdbcUrl()).thenReturn("jdbc:mysql://localhost:3306/javasqlweb_db");
        when(hikariDataSource.getDriverClassName()).thenReturn("com.mysql.cj.jdbc.Driver");
        when(baseConfigDao.getConnData()).thenReturn(Collections.emptyList());
        when(baseDataService.getTargetPoolStats()).thenReturn(new Result<>(true, "", Collections.emptyList()));
        when(dashboardDao.getUserSummary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(userSummary);
        when(dashboardDao.getTrend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(trendPoint));
        when(dashboardDao.getUserRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getDatabaseHotspots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getTableHotspots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getRecentQueries(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.countQueries(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        when(dashboardDao.sumResultRows(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(50L);
        when(dashboardDao.avgQueryConsuming(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(12.3D);

        Result<DashboardResponse> result = backstageService.getDashboard("7d", "hour", 10, 5, 10, 10);

        assertTrue(result.getStatus());
        assertNotNull(result.getData());
        assertEquals("7d", result.getData().getRange());
        assertEquals("day", result.getData().getGrain());
        assertEquals(8, result.getData().getSummary().getTotalUsers());
        assertEquals(2, result.getData().getSummary().getNewUsers());
        assertEquals(5L, result.getData().getSummary().getQueryCount());
        assertEquals(50L, result.getData().getSummary().getTotalReturnedRows());
        verify(baseConfigDao, never()).getConnectConfig(anyInt());
    }

    @Test
    void getDashboardIncludesDynamicPoolSummaryAndList() {
        DashboardSummary userSummary = new DashboardSummary();
        TargetPoolStatBean poolStat = new TargetPoolStatBean();
        poolStat.setServerCode(9);
        poolStat.setServerName("core");
        poolStat.setRuntimeStatus("cooldown");
        poolStat.setInCooldown(true);
        poolStat.setTotalConnections(20);
        poolStat.setThreadsAwaitingConnection(3);

        when(baseConfigDao.getConnData()).thenReturn(Collections.emptyList());
        when(baseDataService.getTargetPoolStats()).thenReturn(new Result<>(true, "", List.of(poolStat)));
        when(dashboardDao.getUserSummary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(userSummary);
        when(dashboardDao.getTrend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getUserRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getDatabaseHotspots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getTableHotspots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.getRecentQueries(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(dashboardDao.countQueries(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(dashboardDao.sumResultRows(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(dashboardDao.avgQueryConsuming(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0D);

        Result<DashboardResponse> result = backstageService.getDashboard("24h", "hour", 10, 5, 10, 10);

        assertTrue(result.getStatus());
        assertEquals(1, result.getData().getSummary().getActiveDynamicPools());
        assertEquals(1, result.getData().getSummary().getCooldownDynamicPools());
        assertEquals(20, result.getData().getSummary().getDynamicPoolConnections());
        assertEquals(3, result.getData().getSummary().getDynamicPoolWaitingThreads());
        assertEquals(1, result.getData().getDynamicTargetPools().size());
    }

    @Test
    void testServerConnectClosesTemporaryOperationOnSuccess() throws Exception {
        ConnectConfigBean server = new ConnectConfigBean();
        server.setCode(7);
        server.setDbServerType("mysql");
        DbOperation operation = mock(DbOperation.class);

        doReturn(operation).when(backstageService).createTemporaryDbOperation(server);
        when(operation.serverHealth()).thenReturn(true);

        Result<String> result = backstageService.testServerConnect(server);

        assertTrue(result.getStatus());
        verify(operation).close();
    }

    @Test
    void testServerConnectClosesTemporaryOperationOnFailure() throws Exception {
        ConnectConfigBean server = new ConnectConfigBean();
        server.setCode(8);
        server.setDbServerType("mysql");
        DbOperation operation = mock(DbOperation.class);

        doReturn(operation).when(backstageService).createTemporaryDbOperation(server);
        doThrow(new SQLTransientConnectionException("connect failed")).when(operation).serverHealth();

        Result<String> result = backstageService.testServerConnect(server);

        assertFalse(result.getStatus());
        verify(operation).close();
    }

    @Test
    void updateServerInvalidatesCachedResourcesAfterSuccess() {
        ConnectConfigBean existingServer = new ConnectConfigBean();
        existingServer.setCode(9);
        ConnectConfigBean updatingServer = new ConnectConfigBean();
        updatingServer.setCode(9);
        updatingServer.setDbServerType("mysql");

        when(baseConfigDao.getConnectConfigByCode(9)).thenReturn(existingServer);

        Result<String> result = backstageService.updateServerData(updatingServer);

        assertTrue(result.getStatus());
        verify(baseDataService).invalidateServerResources(9);
    }

    @Test
    void deleteServerInvalidatesCachedResourcesAfterSuccess() {
        ConnectConfigBean existingServer = new ConnectConfigBean();
        existingServer.setCode(10);

        when(baseConfigDao.getConnectConfig(10)).thenReturn(existingServer);

        Result<String> result = backstageService.delServer(10);

        assertTrue(result.getStatus());
        verify(baseDataService).invalidateServerResources(10);
    }

    @Test
    void resetServerInvalidatesCachedResourcesAfterSuccess() {
        ConnectConfigBean existingServer = new ConnectConfigBean();
        existingServer.setCode(11);

        when(baseConfigDao.getConnectConfig(11)).thenReturn(existingServer);

        Result<String> result = backstageService.resetServer(11);

        assertTrue(result.getStatus());
        verify(baseDataService).invalidateServerResources(11);
    }

    @Test
    void getConnDataShouldMatchServerNameOrExactDbName() {
        ConnectConfigBean serverA = new ConnectConfigBean();
        serverA.setCode(1);
        serverA.setDbServerName("mysql-core");
        serverA.setDbServerType("mysql");
        ConnectConfigBean serverB = new ConnectConfigBean();
        serverB.setCode(2);
        serverB.setDbServerName("analytics-node");
        serverB.setDbServerType("mysql");

        when(baseConfigDao.getConnData()).thenReturn(List.of(serverA, serverB));
        when(baseConfigDao.getServerCodesByDatabaseName("order_db")).thenReturn(List.of(2));

        Result<List<ConnectConfigBean>> result = backstageService.getConnData("order_db", "mysql", "order_db");

        assertTrue(result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals(Integer.valueOf(2), result.getData().get(0).getCode());
    }

    @Test
    void syncServerDatabasesShouldContinueWhenPartiallyFailed() throws Exception {
        ConnectConfigBean serverA = new ConnectConfigBean();
        serverA.setCode(1);
        serverA.setDbServerName("core");
        serverA.setDbServerType("mysql");
        ConnectConfigBean serverB = new ConnectConfigBean();
        serverB.setCode(2);
        serverB.setDbServerName("archive");
        serverB.setDbServerType("mysql");
        DbOperation successOperation = mock(DbOperation.class);
        DbOperation failedOperation = mock(DbOperation.class);

        when(baseConfigDao.getConnData()).thenReturn(List.of(serverA, serverB));
        doReturn(successOperation)
                .doReturn(failedOperation)
                .when(backstageService).createTemporaryDbOperation(org.mockito.ArgumentMatchers.any(ConnectConfigBean.class));
        when(successOperation.getDbList()).thenReturn(List.of(new DatabaseNameBean("order_db"), new DatabaseNameBean("audit_db")));
        when(failedOperation.getDbList()).thenThrow(new RuntimeException("connect timeout"));
        when(baseConfigDao.getLatestServerDatabaseSnapshotTime()).thenReturn("2026-04-28 18:01:00");

        Result<ServerDatabaseSyncResult> result = backstageService.syncServerDatabases();

        assertTrue(result.getStatus());
        assertEquals(2, result.getData().getTotalServers());
        assertEquals(1, result.getData().getSuccessCount());
        assertEquals(1, result.getData().getFailCount());
        assertEquals("2026-04-28 18:01:00", result.getData().getSyncedAt());
        assertEquals(1, result.getData().getFailures().size());
        assertEquals(Integer.valueOf(2), result.getData().getFailures().get(0).getServerCode());
        assertEquals("archive", result.getData().getFailures().get(0).getServerName());
        verify(baseConfigDao).deleteServerDatabaseSnapshots(1);
        verify(baseConfigDao).addServerDatabaseSnapshots(1, List.of("order_db", "audit_db"));
        verify(baseConfigDao, never()).deleteServerDatabaseSnapshots(2);
        verify(successOperation).close();
        verify(failedOperation).close();
    }

    @Test
    void getTargetPoolSessionsEnrichesPlatformUserFromInFlightQueryLog() {
        ConnectConfigBean existingServer = new ConnectConfigBean();
        existingServer.setCode(12);
        TargetSessionStatBean session = new TargetSessionStatBean();
        session.setServerCode(12);
        session.setSessionId("301");
        session.setDatabaseUserName("report_user");
        QueryLogBean queryLogBean = new QueryLogBean();
        queryLogBean.setCode(88);
        queryLogBean.setDbSessionId("301");
        queryLogBean.setQueryName("alice");
        queryLogBean.setQueryConsuming(null);

        when(baseConfigDao.getConnectConfig(12)).thenReturn(existingServer);
        when(baseDataService.getTargetPoolSessions(12)).thenReturn(new Result<>(true, "", List.of(session)));
        when(baseConfigDao.getQueryLogsByServerAndSessionIds(12, List.of("301"))).thenReturn(List.of(queryLogBean));

        Result<List<TargetSessionStatBean>> result = backstageService.getTargetPoolSessions(12);

        assertTrue(result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("alice", result.getData().get(0).getPlatformUserName());
        assertEquals(Integer.valueOf(88), result.getData().get(0).getQueryLogCode());
        assertTrue(Boolean.TRUE.equals(result.getData().get(0).getMatchedByPlatformTrace()));
    }

    @Test
    void getTargetPoolSessionsSkipsPlatformEnrichmentWhenDbSessionIdColumnMissing() {
        ConnectConfigBean existingServer = new ConnectConfigBean();
        existingServer.setCode(13);
        TargetSessionStatBean session = new TargetSessionStatBean();
        session.setServerCode(13);
        session.setSessionId("401");
        session.setDatabaseUserName("report_user");

        when(baseConfigDao.getConnectConfig(13)).thenReturn(existingServer);
        when(baseDataService.getTargetPoolSessions(13)).thenReturn(new Result<>(true, "", List.of(session)));
        when(baseConfigDao.getQueryLogsByServerAndSessionIds(13, List.of("401")))
                .thenThrow(new RuntimeException("Unknown column 'db_session_id' in 'field list'"));

        Result<List<TargetSessionStatBean>> result = backstageService.getTargetPoolSessions(13);

        assertTrue(result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("report_user", result.getData().get(0).getDatabaseUserName());
        assertEquals(null, result.getData().get(0).getPlatformUserName());
        assertFalse(Boolean.TRUE.equals(result.getData().get(0).getMatchedByPlatformTrace()));
    }
}
