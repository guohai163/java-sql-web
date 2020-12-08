package org.guohai.javasqladmin.controller;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.Result;
import org.guohai.javasqladmin.beans.UserBean;
import org.guohai.javasqladmin.service.UserService;
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

    @ResponseBody
    @RequestMapping(value = "/check")
    public Result<UserBean> checkLogin(@RequestHeader(value = "User-Token", required =  false) String token){
        return userService.checkLoginStatus(token);
    }

    /**
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/login")
    public Result<UserBean> login(@RequestBody UserBean user){
        return userService.login(user.getUserName(), user.getPassWord());
    }
}
