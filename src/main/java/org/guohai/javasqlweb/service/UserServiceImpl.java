package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
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
            adminDao.setUserSecret(user.getAuthSecret(),user.getToken(),user.getUserName());
            return new Result<>(true, user);
        }
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

    /**
     * 绑定OTP
     *
     * @param token   用户令牌
     * @param optPass 一次密码
     * @return
     */
    @Override
    public Result<String> bindOtp(String token, String optPass) {
        UserBean user = adminDao.getUserByToken(token);
        if(null == user){
            // 失败
            return new Result<>(false,"token_error");
        }
        if("".equals(user.getAuthSecret()) || OtpAuthStatus.UNBIND!=user.getAuthStatus() ||
                UserLoginStatus.LOGGING==user.getLoginStatus()){
            // 状态异常结束
            return new Result<>(false,"status_error");
        }
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        Boolean authResult = gAuth.authorize(user.getAuthSecret(), Integer.parseInt(optPass));
        if(!authResult){
            return new Result<>(false,"otp_pass_error");
        }
        adminDao.setUserBindStatus(user.getUserName());
        return new Result<>(true,"success");
    }
}
