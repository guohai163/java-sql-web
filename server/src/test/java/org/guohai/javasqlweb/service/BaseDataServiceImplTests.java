package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.DatabaseNameBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
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

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> accessStaticMap(String fieldName) throws Exception {
        Field field = BaseDataServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<Integer, Object>) field.get(null);
    }

    private UserBean buildUser() {
        UserBean user = new UserBean();
        user.setCode(1);
        return user;
    }
}
