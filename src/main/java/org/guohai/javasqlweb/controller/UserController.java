package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
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
     *
     * @param sign
     * @param userName
     * @param timestamp
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/create_user/{user_name}")
    public Result<UserBean> autoCreateUser(@RequestHeader(value = "sign", required =  true) String sign,
                                           @PathVariable("user_name") String userName,
                                           String timestamp) {
        LOG.info(String.format("接收到参数 user: %s, time: %s, sign: %s", userName, timestamp, sign));
        return userService.createUserByLink(userName, timestamp, sign);
    }
}
