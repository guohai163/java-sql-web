package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * OIDC PKCE state 一次性记录
 */
@Data
public class OidcLoginStateBean {

    /** 自增主键 */
    private Integer code;

    /** 状态用途：admin/login */
    private String usageType;

    /** OIDC state 参数 */
    private String stateKey;

    /** 与 state 绑定的 PKCE code_verifier */
    private String codeVerifier;

    /** 过期时间 */
    private Date expireTime;

    /** 创建时间 */
    private Date createdTime;
}
