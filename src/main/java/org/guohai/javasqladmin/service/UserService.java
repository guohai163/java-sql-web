package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.Result;
import org.guohai.javasqladmin.beans.UserBean;

/**
 * 用户操作服务类
 * @author guohai
 */
public interface UserService {

    /**
     * 登录方法
     * @param name
     * @param pass
     * @return
     */
    Result<UserBean> login(String name,String pass);

    /**
     * 注销方法
     * @param token
     * @return
     */
    Result<String> logout(String token);

    /**
     * 检查登录状态
     * @param token
     * @return
     */
    Result<UserBean> checkLoginStatus(String token);
}
