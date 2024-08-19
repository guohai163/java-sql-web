package org.guohai.javasqlweb.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions;
import org.guohai.javasqlweb.beans.Result;

import org.guohai.javasqlweb.service.webauthn.WebAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

/**
 * web auth类
 */
@Controller
@RequestMapping(value = "/webauthn")
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
                                   String publicKeyCredentialJson) throws IOException {
        return webAuthService.register(token, publicKeyCredentialJson);
    }

    /**
     * 登录时第一步请求PublicKeyCredentialRequestOptions
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/get")
    public Result<String> get() throws JsonProcessingException {
        return  webAuthService.get();
    }

    /**
     *
     * @param credential
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/signin")
    public Result<String> signin(PublicKeyCredential credential){
        return null;
    }
}
