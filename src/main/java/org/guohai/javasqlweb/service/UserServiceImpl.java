package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.dao.UserManageDao;
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
    UserManageDao userDao;

    /**
     * 登录方法
     *
     * @param name
     * @param pass
     * @return
     */
    @Override
    public Result<UserBean> login(String name, String pass) {
        UserBean user = userDao.getUserByName(name,pass);
        if(null == user){
            // 登录失败
            return new Result<>(false,"登录失败",null);
        }
        user.setToken(UUID.randomUUID().toString());
        // 检查多因子绑定情况
        if(OtpAuthStatus.UNBIND == user.getAuthStatus() || OtpAuthStatus.BINDING == user.getAuthStatus()) {
            // 未绑定，生成新的秘钥
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            final GoogleAuthenticatorKey key = gAuth.createCredentials();
            user.setAuthSecret(key.getKey());
            userDao.setUserSecret(user.getAuthSecret(),user.getToken(),user.getUserName());
            return new Result<>(true,"success", user);
        }
        if(userDao.setUserToken(name,user.getToken())){
            return new Result<>(true,"success", user);
        }
        return new Result<>(false,"网络异常请重试",null);
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
        UserBean user = userDao.getUserByToken(token);
        if(null == user){
            // 失败
            return new Result<>(false,"未登录",null);
        }
        if(UserLoginStatus.LOGGED != user.getLoginStatus()){
            // 非登录完成状态
            return new Result<>(false,"未登录", null);
        }
        return new Result<>(true,"success", user);
    }

    /**
     * 绑定OTP
     *
     * @param token   用户令牌
     * @param otpPass 一次密码
     * @return
     */
    @Override
    public Result<String> bindOtp(String token, String otpPass) {
        UserBean user = userDao.getUserByToken(token);
        if(null == user){
            // 失败
            return new Result<>(false,"用户还未登录","token_error");
        }
        if("".equals(user.getAuthSecret()) || OtpAuthStatus.BINDING!=user.getAuthStatus() ||
                UserLoginStatus.LOGGING!=user.getLoginStatus()){
            // 状态异常结束
            return new Result<>(false,"状态错误，跳回登录","status_error");
        }
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        Boolean authResult = gAuth.authorize(user.getAuthSecret(), Integer.parseInt(otpPass));
        if(!authResult){
            return new Result<>(false,"一次密码错误，请重新输入","otp_pass_error");
        }
        userDao.setUserBindStatus(user.getUserName());
        return new Result<>(true,"成功","success");
    }

    /**
     * 验证一次密钥
     *
     * @param token
     * @param otpPass
     * @return
     */
    @Override
    public Result<String> verifyOtp(String token, String otpPass) {
        UserBean user = userDao.getUserByToken(token);
        if(null == user){
            // 失败
            return new Result<>(false,"还未登录","token_error");
        }
        if("".equals(user.getAuthSecret()) || OtpAuthStatus.BIND!=user.getAuthStatus() ||
                UserLoginStatus.LOGGING!=user.getLoginStatus()){
            // 状态异常结束
            return new Result<>(false,"状态异常","status_error");
        }
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        Boolean authResult = gAuth.authorize(user.getAuthSecret(), Integer.parseInt(otpPass));
        if(!authResult){
            return new Result<>(false,"动态码错误，请重新输入","otp_pass_error");
        }
        userDao.setUserLoginSuccess(token);
        return new Result<>(true,"成功","success");
    }

    /**
     * 注销用户
     *
     * @param token
     * @return
     */
    @Override
    public Result<String> logoutUser(String token) {
        userDao.logoutUser(token);
        return new Result<>(true,"成功", "");
    }
}
