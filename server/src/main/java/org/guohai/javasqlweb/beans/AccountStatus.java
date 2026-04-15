package org.guohai.javasqlweb.beans;

/**
 * 账号状态
 */
public enum AccountStatus {
    /**
     * 正常可登录
     */
    ACTIVE,
    /**
     * 待激活，需先设置密码并绑定OTP
     */
    PENDING_ACTIVATION,
    /**
     * 待重置密码
     */
    PENDING_PASSWORD_RESET,
    /**
     * 待重绑OTP
     */
    PENDING_OTP_RESET
}
