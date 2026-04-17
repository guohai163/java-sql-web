package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * WebAuthn 一次性请求记录
 */
@Data
public class WebAuthnRequestBean {

    private Integer code;

    private WebAuthnRequestType requestType;

    private String requestKey;

    private String requestJson;

    private Date expireTime;

    private Date createdTime;
}
