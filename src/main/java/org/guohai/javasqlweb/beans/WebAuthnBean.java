package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class WebAuthnBean {

    /**
     * 主键
     */
    private Integer code;
    /**
     * 系统内用户名
     */
    private final String userName;
    private final String userHandle;
    private final String credentialId ;
    private final String publicKey ;
    private final String userAgent;
    private final Date createDate;
}
