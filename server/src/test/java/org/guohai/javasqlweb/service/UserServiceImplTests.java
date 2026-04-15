package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.AccountStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.util.PasswordUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTests {

    @Mock
    private UserManageDao userManageDao;

    @Mock
    private UserSecurityTaskService userSecurityTaskService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void loginReturnsGuidanceWhenAccountIsPendingActivation() {
        UserBean user = new UserBean();
        user.setUserName("alice");
        user.setPassWord(PasswordUtils.encode("Abcd1234!"));
        user.setAccountStatus(AccountStatus.PENDING_ACTIVATION);

        when(userManageDao.getUserLoginDataByName("alice")).thenReturn(user);

        Result<UserBean> result = userService.login("alice", "Abcd1234!");

        assertFalse(result.getStatus());
        assertEquals("账号未激活，请使用管理员发送的激活链接完成初始化密码和OTP绑定", result.getMessage());
        verify(userManageDao, never()).setUserToken(eq("alice"), anyString());
    }

    @Test
    void changePasswordRejectsWeakPassword() {
        UserBean user = new UserBean();
        user.setCode(1);
        user.setLoginStatus(UserLoginStatus.LOGGED);

        when(userManageDao.getUserByToken("token-1")).thenReturn(user);

        Result<String> result = userService.changePassword("token-1", "weakpass");

        assertFalse(result.getStatus());
        assertEquals("密码至少需要包含大写字母、小写字母、数字、特殊字符中的3类", result.getMessage());
        verify(userManageDao, never()).changeUserPasswordByCode(eq(1), anyString());
    }

    @Test
    void changePasswordPersistsBcryptHashWhenPasswordIsStrongEnough() {
        UserBean user = new UserBean();
        user.setCode(1);
        user.setLoginStatus(UserLoginStatus.LOGGED);

        when(userManageDao.getUserByToken("token-1")).thenReturn(user);

        Result<String> result = userService.changePassword("token-1", "Abcd1234!");

        assertTrue(result.getStatus());
        verify(userManageDao).changeUserPasswordByCode(eq(1), anyString());
    }
}
