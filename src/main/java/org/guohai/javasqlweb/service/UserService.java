package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;

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

    /**
     * 绑定OTP
     * @param token 用户令牌
     * @param otpPass 一次密码
     * @return
     */
    Result<String> bindOtp(String token, String otpPass);

    /**
     * 验证一次密钥
     * @param token
     * @param otpPass
     * @return
     */
    Result<String> verifyOtp(String token, String otpPass);

    /**
     * 注销用户
     * @param token
     * @return
     */
    Result<String> logoutUser(String token);
}
