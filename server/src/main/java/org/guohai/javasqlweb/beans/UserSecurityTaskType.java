package org.guohai.javasqlweb.beans;

/**
 * 安全任务类型
 */
public enum UserSecurityTaskType {
    /**
     * 首次激活
     */
    ACTIVATE,
    /**
     * 重置密码
     */
    RESET_PASSWORD,
    /**
     * 重绑OTP
     */
    RESET_OTP
}
