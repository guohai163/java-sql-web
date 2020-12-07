package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.Result;
import org.guohai.javasqladmin.beans.UserBean;
import org.guohai.javasqladmin.dao.AdminDao;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * 用户操作类
 * @author guohai
 */
public class UserServiceImpl implements UserService {
    /**
     * 管理DAO
     */
    @Autowired
    AdminDao adminDao;
    /**
     * 登录方法
     *
     * @param name
     * @param pass
     * @return
     */
    @Override
    public Result<UserBean> login(String name, String pass) {
        UserBean user = adminDao.getUserByName(name,pass);
        if(null == user){
            // 登录失败
            return new Result<>(false,null);
        }
        user.setToken(UUID.randomUUID().toString());
        if(adminDao.setUserToken(name,user.getToken())){
            return new Result<>(true, user);
        }
        return new Result<>(false,null);
    }

    /**
     * 注销方法
     *
     * @param token
     * @return
     */
    @Override
    public Result<String> logout(String token) {

        return null;
    }
}
