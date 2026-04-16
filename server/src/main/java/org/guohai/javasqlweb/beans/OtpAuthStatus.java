package org.guohai.javasqlweb.beans;

/**
 * 用户多因子绑定状态
 * @author guohai
 */
public enum OtpAuthStatus {
    /**
     * 未绑定
     */
    UNBIND,
    /**
     * 绑定状态中
     */
    BINDING,
    /**
     * 绑定
     */
    BIND
}
