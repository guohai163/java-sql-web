package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.DatabaseNameBean;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardResponse;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.QueryLogTargetDao;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseDataServiceImplTests {

    @Mock
    private BaseConfigDao baseConfigDao;

    @Mock
    private QueryLogTargetDao queryLogTargetDao;

    @InjectMocks
    private BaseDataServiceImpl baseDataService;

    @BeforeEach
    @AfterEach
    void resetStaticState() throws Exception {
        accessStaticMap("operationMap").clear();
        accessStaticMap("connectionFailureCountMap").clear();
        accessStaticMap("connectionCooldownUntilMap").clear();
        accessStaticMap("connectionLastErrorMap").clear();
        accessStaticMap("workbenchServerDashboardCache").clear();
        accessStaticMap("workbenchDatabaseDashboardCache").clear();
    }

    @Test
    void getDbNameEntersCooldownAfterThreeConnectionFailures() throws Exception {
        UserBean user = buildUser();
        DbOperation operation = mock(DbOperation.class);

        when(baseConfigDao.hasServerPermission(user.getCode(), 7)).thenReturn(true);
        when(operation.getDbList()).thenThrow(new SQLTransientConnectionException("connect failed"));
        accessStaticMap("operationMap").put(7, operation);

        assertFalse(baseDataService.getDbName(7, user).getStatus());
        assertFalse(baseDataService.getDbName(7, user).getStatus());
        assertFalse(baseDataService.getDbName(7, user).getStatus());

        Result<List<DatabaseNameBean>> cooldownResult = baseDataService.getDbName(7, user);

        assertFalse(cooldownResult.getStatus());
        verify(operation, times(3)).getDbList();
    }

    @Test
    void successfulCallClearsFailureCounter() throws Exception {
        UserBean user = buildUser();
        DbOperation operation = mock(DbOperation.class);
        SQLTransientConnectionException connectionException = new SQLTransientConnectionException("connect failed");

        when(baseConfigDao.hasServerPermission(user.getCode(), 8)).thenReturn(true);
        when(operation.getDbList())
                .thenThrow(connectionException)
                .thenThrow(connectionException)
                .thenReturn(List.of(new DatabaseNameBean("core")))
                .thenThrow(connectionException)
                .thenThrow(connectionException)
                .thenThrow(connectionException);
        accessStaticMap("operationMap").put(8, operation);

        assertFalse(baseDataService.getDbName(8, user).getStatus());
        assertFalse(baseDataService.getDbName(8, user).getStatus());
        assertTrue(baseDataService.getDbName(8, user).getStatus());
        assertFalse(baseDataService.getDbName(8, user).getStatus());
        assertFalse(baseDataService.getDbName(8, user).getStatus());
        assertFalse(baseDataService.getDbName(8, user).getStatus());

        Result<List<DatabaseNameBean>> cooldownResult = baseDataService.getDbName(8, user);

        assertFalse(cooldownResult.getStatus());
        verify(operation, times(6)).getDbList();
    }

    @Test
    void sqlErrorDoesNotTriggerCooldown() throws Exception {
        UserBean user = buildUser();
        DbOperation operation = mock(DbOperation.class);

        when(baseConfigDao.hasServerPermission(user.getCode(), 9)).thenReturn(true);
        when(operation.getDbList()).thenThrow(new SQLException("syntax error", "42000"));
        accessStaticMap("operationMap").put(9, operation);

        for (int i = 0; i < 4; i++) {
            Result<List<DatabaseNameBean>> result = baseDataService.getDbName(9, user);
            assertFalse(result.getStatus());
            assertFalse(result.getMessage().contains("冷却"));
        }

        verify(operation, times(4)).getDbList();
    }

    @Test
    void workbenchDashboardUsesCacheUntilForceRefresh() throws Exception {
        UserBean user = buildUser();
        DbOperation operation = mock(DbOperation.class);
        ConnectConfigBean connectConfigBean = new ConnectConfigBean();
        connectConfigBean.setCode(11);
        connectConfigBean.setDbServerType("mysql");
        connectConfigBean.setDbServerName("core");
        connectConfigBean.setDbServerHost("127.0.0.1");

        when(baseConfigDao.hasServerPermission(user.getCode(), 11)).thenReturn(true);
        when(baseConfigDao.getConnectConfig(11)).thenReturn(connectConfigBean);
        when(operation.queryDatabaseBySql(anyString(), anyString(), anyInt()))
                .thenReturn(buildDashboardResult("demo"));
        accessStaticMap("operationMap").put(11, operation);

        Result<WorkbenchDashboardResponse> firstResult = baseDataService.getWorkbenchDashboard(11, "demo", user, false);
        assertTrue(firstResult.getStatus());
        assertNotNull(firstResult.getData());
        clearInvocations(operation);

        Result<WorkbenchDashboardResponse> cachedResult = baseDataService.getWorkbenchDashboard(11, "demo", user, false);
        assertTrue(cachedResult.getStatus());
        verify(operation, times(0)).queryDatabaseBySql(anyString(), anyString(), anyInt());

        baseDataService.getWorkbenchDashboard(11, "demo", user, true);
        verify(operation, atLeastOnce()).queryDatabaseBySql(anyString(), anyString(), anyInt());
    }

    @SuppressWarnings("unchecked")
    @Test
    void invalidateServerResourcesClosesOperationAndClearsAllServerState() throws Exception {
        DbOperation operation = mock(DbOperation.class);
        long now = System.currentTimeMillis();
        accessStaticMap("operationMap").put(12, operation);
        accessStaticMap("connectionFailureCountMap").put(12, 2);
        accessStaticMap("connectionCooldownUntilMap").put(12, now + 10_000L);
        accessStaticMap("connectionLastErrorMap").put(12, "connect failed");
        accessStaticMap("workbenchServerDashboardCache").put(12, createDashboardCacheEntry(now, now + 60_000L));
        accessStaticMap("workbenchDatabaseDashboardCache").put("12::demo", createDashboardCacheEntry(now, now + 60_000L));

        baseDataService.invalidateServerResources(12);

        verify(operation).close();
        assertFalse(accessStaticMap("operationMap").containsKey(12));
        assertFalse(accessStaticMap("connectionFailureCountMap").containsKey(12));
        assertFalse(accessStaticMap("connectionCooldownUntilMap").containsKey(12));
        assertFalse(accessStaticMap("connectionLastErrorMap").containsKey(12));
        assertFalse(accessStaticMap("workbenchServerDashboardCache").containsKey(12));
        assertFalse(accessStaticMap("workbenchDatabaseDashboardCache").containsKey("12::demo"));
    }

    @Test
    void getWorkbenchDashboardSweepsExpiredEntriesForOtherKeys() throws Exception {
        UserBean user = buildUser();
        DbOperation operation = mock(DbOperation.class);
        ConnectConfigBean connectConfigBean = new ConnectConfigBean();
        connectConfigBean.setCode(11);
        connectConfigBean.setDbServerType("mysql");
        connectConfigBean.setDbServerName("core");
        connectConfigBean.setDbServerHost("127.0.0.1");
        long now = System.currentTimeMillis();

        accessStaticMap("workbenchServerDashboardCache").put(99, createDashboardCacheEntry(now - 60_000L, now - 1L));
        accessStaticMap("workbenchDatabaseDashboardCache").put("99::archive", createDashboardCacheEntry(now - 60_000L, now - 1L));
        when(baseConfigDao.hasServerPermission(user.getCode(), 11)).thenReturn(true);
        when(baseConfigDao.getConnectConfig(11)).thenReturn(connectConfigBean);
        when(operation.queryDatabaseBySql(anyString(), anyString(), anyInt()))
                .thenReturn(buildDashboardResult("demo"));
        accessStaticMap("operationMap").put(11, operation);

        Result<WorkbenchDashboardResponse> result = baseDataService.getWorkbenchDashboard(11, "demo", user, false);

        assertTrue(result.getStatus());
        assertFalse(accessStaticMap("workbenchServerDashboardCache").containsKey(99));
        assertFalse(accessStaticMap("workbenchDatabaseDashboardCache").containsKey("99::archive"));
    }

    private static Map<Object, Object> accessStaticMap(String fieldName) throws Exception {
        Field field = BaseDataServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }

    private static Object createDashboardCacheEntry(long cachedAt, long expiresAt) throws Exception {
        Class<?> entryClass = Class.forName(BaseDataServiceImpl.class.getName() + "$WorkbenchDashboardCacheEntry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(List.class, long.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of(), cachedAt, expiresAt);
    }

    private UserBean buildUser() {
        UserBean user = new UserBean();
        user.setCode(1);
        return user;
    }

    private Object[] buildDashboardResult(String value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        return new Object[]{1, 1, List.of(row)};
    }
}
