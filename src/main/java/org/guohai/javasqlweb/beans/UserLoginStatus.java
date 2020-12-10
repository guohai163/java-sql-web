package org.guohai.javasqlweb.beans;

/**
 * 登录状态枚举
 * @author guohai
 */
public enum UserLoginStatus {

    /**
     * 注销状态
     */
    LOGOUT,
    /**
     * 登录中，待验证令牌
     */
    LOGGING,
    /**
     * 登录成功
     */
    LOGGED
}
