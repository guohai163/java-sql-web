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
     * 校验业务查询接口的认证信息
     * @param token 短期登录态
     * @param authorizationHeader Bearer 头
     * @return 认证结果
     */
    Result<UserBean> checkApiAccess(String token, String authorizationHeader);

    /**
     * 校验后台管理接口认证
     * @param token 短期登录态
     * @return 认证结果
     */
    Result<UserBean> checkAdminAccess(String token);

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

    /**
     * 通过链接创建用户
     * @param user
     * @param time
     * @param sign
     * @return
     */
    Result<UserBean> createUserByLink(String user, String time, String sign);

    /**
     * 获取访问令牌信息
     * @param token 短期登录态
     * @return 令牌信息
     */
    Result<UserBean> getAccessTokenInfo(String token);

    /**
     * 申请访问令牌
     * @param token 短期登录态
     * @return 令牌信息
     */
    Result<UserBean> createAccessToken(String token);

    /**
     * 续期访问令牌
     * @param token 短期登录态
     * @return 令牌信息
     */
    Result<UserBean> renewAccessToken(String token);

    /**
     * 重置访问令牌
     * @param token 短期登录态
     * @return 令牌信息
     */
    Result<UserBean> resetAccessToken(String token);
}
