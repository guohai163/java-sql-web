package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.dao.AdminDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户操作类
 * @author guohai
 */
@Service
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
        // 检查多因子绑定情况
        if(OtpAuthStatus.UNBIND == user.getAuthStatus()) {
            // 未绑定，生成新的秘钥
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            final GoogleAuthenticatorKey key = gAuth.createCredentials();
            user.setAuthSecret(key.getKey());

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

    /**
     * 检查登录状态
     *
     * @param token
     * @return
     */
    @Override
    public Result<UserBean> checkLoginStatus(String token) {
        UserBean user = adminDao.getUserByToken(token);
        if(null == user){
            // 失败
            return new Result<>(false,null);
        }
        return new Result<>(true, user);
    }
}
