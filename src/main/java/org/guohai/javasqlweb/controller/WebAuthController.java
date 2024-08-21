package org.guohai.javasqlweb.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions;
import org.guohai.javasqlweb.beans.Result;

import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.webauthn.WebAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * web auth类
 */
@Controller
@RequestMapping(value = "/webauthn")
@CrossOrigin
public class WebAuthController {

    @Autowired
    WebAuthService webAuthService;

    /**
     * 第一步服务 端创建PublicKeyCredentialCreationOptions对象 ，
     * @param token
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/create")
    public Result<String> create(@RequestHeader(value = "User-Token", required =  false) String token){
        return webAuthService.create(token);
    }

    /**
     * 第二步，对于 用户端签名后的结果进行注册
     * @param token
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/register")
    public Result<String> register(@RequestHeader(value = "User-Token", required =  false) String token,
                                   @RequestBody  String body) throws IOException {
        return webAuthService.register(token, body);
    }

    /**
     * 登录时第一步请求PublicKeyCredentialRequestOptions
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/get")
    public Result<String> get(@RequestHeader(value = "Session-key", required =  false) String session) throws JsonProcessingException {
        return  webAuthService.get(session);
    }

    /**
     *
     * @param body
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/signin")
    public Result<UserBean> signin(@RequestHeader(value = "Session-key", required =  false) String session,
                                   @RequestBody  String body) throws IOException {
        return webAuthService.signIn(body,session);
    }
}
