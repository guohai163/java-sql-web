package org.guohai.javasqlweb.beans;

/**
 * 安全任务状态
 */
public enum UserSecurityTaskStatus {
    /**
     * 待设置密码
     */
    PENDING_PASSWORD,
    /**
     * 待绑定OTP
     */
    PENDING_OTP,
    /**
     * 已使用
     */
    USED,
    /**
     * 已过期
     */
    EXPIRED,
    /**
     * 已取消
     */
    CANCELLED
}
