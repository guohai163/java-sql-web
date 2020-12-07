package org.guohai.javasqladmin.beans;

import lombok.Data;

/**
 * 用户实体类
 * @author guohai
 */
@Data
public class UserBean {
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
}
