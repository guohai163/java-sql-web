package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.dao.UserSecurityTaskDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSecurityTaskServiceImplTests {

    @Mock
    private UserManageDao userManageDao;

    @Mock
    private UserSecurityTaskDao userSecurityTaskDao;

    @InjectMocks
    private UserSecurityTaskServiceImpl userSecurityTaskService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userSecurityTaskService, "projectHost", "https://jsw.example.com");
    }

    @Test
    void createActivationTaskDerivesUserNameFromEmailAndReturnsLink() {
        UserBean createdUser = new UserBean();
        createdUser.setCode(10);
        createdUser.setUserName("alice");
        createdUser.setEmail("alice@example.com");
        createdUser.setAccountStatus(AccountStatus.PENDING_ACTIVATION);

        when(userManageDao.getUserByEmail("alice@example.com")).thenReturn(null);
        when(userManageDao.getUserByName("alice")).thenReturn(null, createdUser);
        when(userSecurityTaskDao.addTask(any(UserSecurityTaskBean.class))).thenReturn(true);

        Result<LinkIssueResult> result = userSecurityTaskService.createActivationTask("admin-token", "Alice@example.com");

        assertTrue(result.getStatus());
        assertEquals("alice", result.getData().getUserName());
        assertEquals("alice@example.com", result.getData().getEmail());
        assertEquals(UserSecurityTaskType.ACTIVATE, result.getData().getTaskType());
        assertTrue(result.getData().getLinkUrl().startsWith("https://jsw.example.com/security-task?uuid="));
        verify(userManageDao).addNewUser(eq("alice"), eq("alice@example.com"), anyString(),
                eq(AccountStatus.PENDING_ACTIVATION.name()));
        verify(userSecurityTaskDao).cancelPendingTasksByUser(10);
    }

    @Test
    void createPasswordResetTaskRejectsPendingActivationUser() {
        UserBean user = new UserBean();
        user.setCode(9);
        user.setUserName("alice");
        user.setAccountStatus(AccountStatus.PENDING_ACTIVATION);

        when(userManageDao.getUserByName("alice")).thenReturn(user);

        Result<LinkIssueResult> result = userSecurityTaskService.createPasswordResetTask("admin-token", "alice");

        assertFalse(result.getStatus());
        assertEquals("该账号尚未激活，请重发激活链接", result.getMessage());
    }

    @Test
    void submitPasswordForActivationMovesTaskIntoOtpStep() {
        UserSecurityTaskBean task = new UserSecurityTaskBean();
        task.setCode(1);
        task.setUserCode(2);
        task.setUserName("alice");
        task.setEmail("alice@example.com");
        task.setTaskType(UserSecurityTaskType.ACTIVATE);
        task.setTaskStatus(UserSecurityTaskStatus.PENDING_PASSWORD);
        task.setExpireTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        UserBean user = new UserBean();
        user.setCode(2);
        user.setUserName("alice");
        user.setEmail("alice@example.com");
        user.setAuthStatus(OtpAuthStatus.UNBIND);

        when(userSecurityTaskDao.getTaskByHash(anyString())).thenReturn(task);
        when(userManageDao.getUserByCode(2)).thenReturn(user);

        Result<SecurityTaskInfo> result = userSecurityTaskService.submitPassword("uuid-123", "Abcd1234!");

        assertTrue(result.getStatus());
        assertEquals(UserSecurityTaskStatus.PENDING_OTP, result.getData().getTaskStatus());
        assertNotNull(result.getData().getToken());
        assertNotNull(result.getData().getAuthSecret());
        verify(userManageDao).changeUserPasswordByCode(eq(2), anyString());
        verify(userManageDao).setUserSecret(anyString(), anyString(), eq("alice"));
        verify(userSecurityTaskDao).updateTaskStatus(1, UserSecurityTaskStatus.PENDING_OTP.name());
    }

    @Test
    void bindOtpUsesPersistedUserTokenWhenValidatingSession() {
        UserSecurityTaskBean task = new UserSecurityTaskBean();
        task.setCode(11);
        task.setUserCode(2);
        task.setUserName("alice");
        task.setTaskType(UserSecurityTaskType.RESET_OTP);
        task.setTaskStatus(UserSecurityTaskStatus.PENDING_OTP);
        task.setExpireTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        UserBean user = new UserBean();
        user.setCode(2);
        user.setUserName("alice");
        user.setToken("token-123");
        user.setAuthSecret("AABBCCDDEEFFGGHH");

        when(userSecurityTaskDao.getTaskByHash(anyString())).thenReturn(task);
        when(userManageDao.getUserByCode(2)).thenReturn(user);

        Result<String> result = userSecurityTaskService.bindOtp("uuid-123", "token-123", "abc");

        assertFalse(result.getStatus());
        assertEquals("动态码格式错误", result.getMessage());
    }

    @Test
    void getTaskInfoExpiresOutdatedTask() {
        UserSecurityTaskBean task = new UserSecurityTaskBean();
        task.setCode(1);
        task.setUserCode(2);
        task.setUserName("alice");
        task.setEmail("alice@example.com");
        task.setTaskType(UserSecurityTaskType.RESET_PASSWORD);
        task.setTaskStatus(UserSecurityTaskStatus.PENDING_PASSWORD);
        task.setExpireTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        when(userSecurityTaskDao.getTaskByHash(anyString())).thenReturn(task);

        Result<SecurityTaskInfo> result = userSecurityTaskService.getTaskInfo("uuid-123");

        assertFalse(result.getStatus());
        assertEquals("该链接已过期，请联系管理员重新生成", result.getMessage());
        verify(userSecurityTaskDao).markExpired(1);
    }
}
