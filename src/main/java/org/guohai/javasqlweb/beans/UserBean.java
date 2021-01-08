package org.guohai.javasqlweb.beans;

import lombok.Data;



/**
 * 用户实体类
 * @author guohai
 */
@Data
public class UserBean {
    /**
     * 用户编号
     */
    private Integer code;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 密码，创建用户时使用
     */
    private String passWord;
    /**
     * 登录令牌
     */
    private String token;

    /**
     * 二次验证密钥
     */
    private String authSecret;

    /**
     * 绑定状态
     */
    private OtpAuthStatus authStatus;

    /**
     * 用户登录状态
     */
    private UserLoginStatus loginStatus;

    /**
     * 一次密码
     */
    private String otpPass;

    @Override
    public String toString(){
        return String.format("user:%s",userName);
    }
}
