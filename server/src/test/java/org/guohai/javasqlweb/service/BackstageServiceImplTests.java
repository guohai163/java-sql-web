package org.guohai.javasqlweb.service;

import com.zaxxer.hikari.HikariDataSource;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.DashboardDao;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    }
}
