package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.controller.UserController;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户操作类
 * @author guohai
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOG  = LoggerFactory.getLogger(UserServiceImpl.class);
    /**
     * 管理DAO
     */
    @Autowired
    UserManageDao userDao;

    @Value("${project.signkey}")
    private String signKey;

    private static Long FW = Long.valueOf(1000*60*5);

    /**
     * 登录方法
     *
     * @param name
     * @param pass
     * @return
     */
    @Override
    public Result<UserBean> login(String name, String pass) {
        UserBean user = userDao.checkUserNamePass(name,pass);
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
        userDao.logoutUser(token);
        return new Result<>(true,"注销成功", "success");
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
        user.setAuthSecret("");
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

    /**
     * 通过链接创建用户
     *
     * @param userName
     * @param time
     * @param sign
     * @return
     */
    @Override
    public Result<UserBean> createUserByLink(String userName, String time, String sign) {
        // 获取当前时间
        Long currentTime = System.currentTimeMillis();

        Long requestTime = Long.parseLong(time)*1000;
        LOG.info(String.format("当前时间: %d ,传入时间: %d", currentTime, requestTime));
        if(currentTime-FW>requestTime || currentTime+FW < requestTime){
            // 超出范围
            return new Result<>(false, "时间范围异常", null);
        }
        if(!sign.equals(Utils.MD5(String.format("%s%s%s",userName, time, signKey)))) {
            return new Result<>(false, "签名没通过", null);
        }
        UserBean userBean = userDao.getUserByName(userName);
        LOG.info(String.format("数据库查询的用户状态，用户名%s,密保状态%s"));
        if( null == userBean){
            // 没有该用户，准备创建用户
            userDao.addNewUser(userName, userName);
            userBean = new UserBean();
            userBean.setUserName(userName);
            userBean.setAuthStatus(OtpAuthStatus.UNBIND);
        }
        userBean.setToken(UUID.randomUUID().toString());

        if(OtpAuthStatus.UNBIND == userBean.getAuthStatus() || OtpAuthStatus.BINDING == userBean.getAuthStatus()) {
            // 未绑定，生成新的秘钥
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            final GoogleAuthenticatorKey key = gAuth.createCredentials();
            userBean.setAuthSecret(key.getKey());
            userDao.setUserSecret(userBean.getAuthSecret(),userBean.getToken(),userBean.getUserName());
            return new Result<>(true,"success", userBean);
        }
        if(userDao.setUserToken(userName,userBean.getToken())){
            return new Result<>(true,"success", userBean);
        }
        return null;
    }
}
