package org.guohai.javasqlweb.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;


/**
 * 用户实体类
 * @author guohai
 */
@Data
public class UserBean {
    /**
     * 用户编号
     */
    private Integer code;
    /**
     * 用户名
     */
    private String userName;

    /**
     * 邮箱
     */
    private String email;
    /**
     * 密码，创建用户时使用
     */
    private String passWord;

    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 登录令牌
     */
    private String token;

    /**
     * 二次验证密钥
     */
    private String authSecret;

    /**
     * 绑定状态
     */
    private OtpAuthStatus authStatus;

    /**
     * 用户登录状态
     */
    private UserLoginStatus loginStatus;

    /**
     * 账号状态
     */
    private AccountStatus accountStatus;

    /**
     * 一次密码
     */
    private String otpPass;

    /**
     * 长期访问令牌
     */
    private String accessToken;

    /**
     * 长期访问令牌哈希
     */
    @JsonIgnore
    private String accessTokenHash;

    /**
     * 访问令牌过期时间
     */
    private Date accessTokenExpireTime;

    /**
     * 访问令牌状态
     */
    private String accessTokenStatus;

    /**
     * 待处理安全任务类型
     */
    private UserSecurityTaskType pendingSecurityTaskType;

    /**
     * 待处理安全任务过期时间
     */
    private Date pendingSecurityTaskExpireTime;

    /**
     * 是否已有访问令牌
     */
    private Boolean hasAccessToken;

    /**
     * 脱敏后的访问令牌
     */
    private String maskedAccessToken;

    /**
     * 当前响应中是否显示完整令牌
     */
    private Boolean accessTokenFullVisible;

    /**
     * 是否允许申请访问令牌
     */
    private Boolean canCreateAccessToken;

    /**
     * 是否允许续期访问令牌
     */
    private Boolean canRenewAccessToken;

    /**
     * 是否允许重置访问令牌
     */
    private Boolean canResetAccessToken;

    @Override
    public String toString(){
        return String.format("user:%s",userName);
    }
}
