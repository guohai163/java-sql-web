package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.SecurityTaskInfo;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.config.LoginRequired;
import org.guohai.javasqlweb.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户操作控制器
 * @author guohai
 */
@RestController
@RequestMapping(value = "/user")
@CrossOrigin
public class UserController {

    private static final Logger LOG  = LoggerFactory.getLogger(UserController.class);

    @Autowired
    UserService userService;

    /**
     * 检查登录状态
     * @param token
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/check")
    public Result<UserBean> checkLogin(@RequestHeader(value = "User-Token", required =  false) String token){
        return userService.checkLoginStatus(token);
    }

    /**
     * 用户登录方法，提交用户名和密码，如果返回未绑定和密钥，需要进行二次验证绑定
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/login")
    public Result<UserBean> login(@RequestBody UserBean user){
        return userService.login(user.getUserName(), user.getPassWord());
    }

    /**
     * 注销
     * @param token
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/logout")
    public Result<String> logout(@RequestHeader(value = "User-Token", required =  false) String token){
        return userService.logout(token);
    }

    /**
     * 绑定用户令牌
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/bindotp")
    public Result<String> bindOtp(@RequestBody UserBean user){
        return userService.bindOtp(user.getToken(), user.getOtpPass());
    }

    /**
     * 验证用户密钥
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/verifyotp")
    public Result<String> verifyOtp(@RequestBody UserBean user){
        return userService.verifyOtp(user.getToken(), user.getOtpPass());
    }

    /**
     * 已登录用户修改密码
     * @param token 登录令牌
     * @param user 密码载体
     * @return 结果
     */
    @LoginRequired
    @ResponseBody
    @RequestMapping(value = "/password", method = RequestMethod.POST)
    public Result<String> changePassword(@RequestHeader(value = "User-Token", required = false) String token,
                                         @RequestBody UserBean user) {
        return userService.changePassword(token, user.getPassWord());
    }

    /**
     * 获取当前用户访问令牌信息
     * @param token 登录令牌
     * @return 访问令牌信息
     */
    @ResponseBody
    @RequestMapping(value = "/access-token", method = RequestMethod.GET)
    public Result<UserBean> getAccessTokenInfo(
            @RequestHeader(value = "User-Token", required = false) String token) {
        return userService.getAccessTokenInfo(token);
    }

    /**
     * 首次生成访问令牌
     * @param token 登录令牌
     * @return 访问令牌信息
     */
    @ResponseBody
    @RequestMapping(value = "/access-token", method = RequestMethod.POST)
    public Result<UserBean> createAccessToken(
            @RequestHeader(value = "User-Token", required = false) String token) {
        return userService.createAccessToken(token);
    }

    /**
     * 续期访问令牌
     * @param token 登录令牌
     * @return 访问令牌信息
     */
    @ResponseBody
    @RequestMapping(value = "/access-token/renew", method = RequestMethod.PUT)
    public Result<UserBean> renewAccessToken(
            @RequestHeader(value = "User-Token", required = false) String token) {
        return userService.renewAccessToken(token);
    }

    /**
     * 重置访问令牌
     * @param token 登录令牌
     * @return 访问令牌信息
     */
    @ResponseBody
    @RequestMapping(value = "/access-token/reset", method = RequestMethod.PUT)
    public Result<UserBean> resetAccessToken(
            @RequestHeader(value = "User-Token", required = false) String token) {
        return userService.resetAccessToken(token);
    }

    /**
     * 查询安全任务信息
     * @param uuid 任务UUID
     * @return 任务信息
     */
    @ResponseBody
    @RequestMapping(value = "/security-task/{uuid}", method = RequestMethod.GET)
    public Result<SecurityTaskInfo> getSecurityTask(@PathVariable("uuid") String uuid) {
        return userService.getSecurityTaskInfo(uuid);
    }

    /**
     * 提交安全任务密码
     * @param uuid 任务UUID
     * @param user 请求体
     * @return 任务信息
     */
    @ResponseBody
    @RequestMapping(value = "/security-task/{uuid}/password", method = RequestMethod.POST)
    public Result<SecurityTaskInfo> submitSecurityTaskPassword(@PathVariable("uuid") String uuid,
                                                               @RequestBody UserBean user) {
        return userService.submitSecurityTaskPassword(uuid, user.getPassWord());
    }

    /**
     * 创建安全任务OTP会话
     * @param uuid 任务UUID
     * @return 任务信息
     */
    @ResponseBody
    @RequestMapping(value = "/security-task/{uuid}/otp-session", method = RequestMethod.POST)
    public Result<SecurityTaskInfo> createSecurityTaskOtpSession(@PathVariable("uuid") String uuid) {
        return userService.createSecurityTaskOtpSession(uuid);
    }

    /**
     * 通过安全任务绑定OTP
     * @param uuid 任务UUID
     * @param user 请求体
     * @return 结果
     */
    @ResponseBody
    @RequestMapping(value = "/security-task/{uuid}/bind-otp", method = RequestMethod.POST)
    public Result<String> bindSecurityTaskOtp(@PathVariable("uuid") String uuid,
                                              @RequestBody UserBean user) {
        return userService.bindSecurityTaskOtp(uuid, user.getToken(), user.getOtpPass());
    }
}
