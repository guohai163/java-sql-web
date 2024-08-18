package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.webauth.PublicKeyCredential;
import org.guohai.javasqlweb.beans.webauth.PublicKeyCredentialCreationOptions;
import org.guohai.javasqlweb.beans.webauth.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * web auth类
 */
@Controller
public class WebAuthController {


    /**
     * 第一步服务 端创建PublicKeyCredentialCreationOptions对象 ，
     * @param token
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/create")
    public Result<PublicKeyCredentialCreationOptions> create(@RequestHeader(value = "User-Token", required =  false) String token){
        return null;
    }

    /**
     * 第二步，对于 用户端签名后的结果进行注册
     * @param token
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/register")
    public Result<String> register(@RequestHeader(value = "User-Token", required =  false) String token,
                                   PublicKeyCredential credential){
        return null;
    }

    /**
     * 登录时第一步请求PublicKeyCredentialRequestOptions
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/get")
    public Result<PublicKeyCredentialRequestOptions> get(){
        return null;
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
