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
}
