package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.*;
import org.guohai.javasqlweb.beans.UserBean;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 管理操作类
 * @author guohai
 */
@Repository
public interface UserManageDao {

    /**
     * 按用户名获取登录鉴权所需字段
     * @param name 用户名
     * @return 用户
     */
    @Select("SELECT code,user_name,email,create_time,pass_word,auth_status,auth_secret,login_status,account_status,access_token_hash,access_token_expire_time " +
            "FROM user_tb WHERE user_name=#{name}")
    UserBean getUserLoginDataByName(@Param("name") String name);

    /**
     * 通过用户名检查用户是否存在
     * @param name
     * @return
     */
    @Select("SELECT code,user_name,email,create_time,auth_status,account_status,access_token_hash,access_token_expire_time FROM user_tb WHERE user_name=#{name}")
    UserBean getUserByName(@Param("name") String name);

    /**
     * 通过邮箱检查用户是否存在
     * @param email 邮箱
     * @return 用户
     */
    @Select("SELECT code,user_name,email,create_time,auth_status,account_status,access_token_hash,access_token_expire_time FROM user_tb WHERE email=#{email}")
    UserBean getUserByEmail(@Param("email") String email);

    /**
     * 通过用户编号查询用户
     * @param userCode 用户编号
     * @return 用户
     */
    @Select("SELECT code,user_name,email,create_time,pass_word,token,auth_status,auth_secret,login_status,account_status,access_token_hash,access_token_expire_time " +
            "FROM user_tb WHERE code=#{userCode}")
    UserBean getUserByCode(@Param("userCode") Integer userCode);

    /**
     * 更新用户登录令牌
     * @param name
     * @param token
     * @return
     */
    @Update("UPDATE user_tb SET token=#{token},login_status='LOGGING' WHERE user_name=#{name}")
    Boolean setUserToken(@Param("name") String name, @Param("token") String token);

    /**
     * 通过用户令牌查找用户
     * @param token
     * @return
     */
    @Select("SELECT code,user_name,email,create_time,pass_word,auth_status,auth_secret,login_status,account_status,access_token_hash,access_token_expire_time " +
            "FROM user_tb WHERE token=#{token}")
    UserBean getUserByToken(@Param("token") String token);

    /**
     * 通过访问令牌哈希查找用户
     * @param accessTokenHash 访问令牌哈希
     * @return 用户
     */
    @Select("SELECT code,user_name,email,create_time,auth_status,account_status,access_token_hash,access_token_expire_time FROM user_tb WHERE access_token_hash=#{accessTokenHash}")
    UserBean getUserByAccessTokenHash(@Param("accessTokenHash") String accessTokenHash);

    /**
     * 设置用户二次验证的密钥,和登录临时token
     * @param secret
     * @param token
     * @param user
     * @return
     */
    @Update("UPDATE user_tb SET auth_secret=#{secret},auth_status='BINDING',token=#{token},login_status='LOGGING' " +
            "WHERE user_name=#{user}")
    Boolean setUserSecret(@Param("secret") String secret, @Param("token") String token, @Param("user") String user);

    /**
     * 绑定成功
     * @param user
     * @return
     */
    @Update("UPDATE user_tb SET auth_status='BIND',login_status='LOGGED' WHERE user_name=#{user}")
    Boolean setUserBindStatus(@Param("user") String user);

    /**
     * 通过令牌查询用户二次验证密钥
     * @param token
     * @return
     */
    @Select("SELECT auth_secret,auth_status FROM user_tb WHERE token=#{token}")
    UserBean getUserSecret(@Param("token") String token);

    /**
     * 设置登录成功状态
     * @param token
     * @return
     */
    @Update("UPDATE user_tb SET login_status='LOGGED' WHERE token=#{token}")
    Boolean setUserLoginSuccess(@Param("token") String token);

    /**
     * 用户注销
     * @param token
     * @return
     */
    @Update("UPDATE user_tb SET token='',login_status='LOGOUT' WHERE token=#{token} ")
    Boolean logoutUser(@Param("token") String token);

    /**
     * 安全的获取一个用户列表
     * @return
     */
    @Select("SELECT code,user_name,email,create_time,auth_status,account_status FROM user_tb;")
    List<UserBean> getUserList();

    /**
     * 安全地获取用户列表以及令牌状态字段
     * @return 用户列表
     */
    @Select("SELECT code,user_name,email,create_time,auth_status,account_status,access_token_hash,access_token_expire_time, " +
            "(SELECT task_type FROM user_security_task_tb t " +
            "WHERE t.user_code = user_tb.code AND t.task_status IN ('PENDING_PASSWORD','PENDING_OTP') " +
            "ORDER BY t.created_time DESC LIMIT 1) AS pending_security_task_type, " +
            "(SELECT expire_time FROM user_security_task_tb t " +
            "WHERE t.user_code = user_tb.code AND t.task_status IN ('PENDING_PASSWORD','PENDING_OTP') " +
            "ORDER BY t.created_time DESC LIMIT 1) AS pending_security_task_expire_time " +
            "FROM user_tb;")
    List<UserBean> getUserListWithAccessToken();

    /**
     * 增加新用户
     * @param userName 用户名
     * @param email 邮箱
     * @param passwordHash 哈希后的密码
     * @return
     */
    @Insert("INSERT INTO `user_tb` (`user_name`,`email`,`create_time`,`pass_word`,`token`,`account_status`,`auth_status`,`login_status`) VALUES" +
            "(#{name},#{email},NOW(),#{passwordHash},'',#{accountStatus},'UNBIND','LOGOUT');")
    Boolean addNewUser(@Param("name") String userName,
                       @Param("email") String email,
                       @Param("passwordHash") String passwordHash,
                       @Param("accountStatus") String accountStatus);

    /**
     * 删除指定用户
     * @param userName
     * @return
     */
    @Delete("DELETE FROM `user_tb` WHERE user_name=#{name};")
    Boolean delUser(@Param("name") String userName);

    /**
     * 按用户编号修改密码
     * @param userCode 用户编号
     * @param passwordHash 新密码哈希
     * @return
     */
    @Update("UPDATE user_tb SET pass_word=#{passwordHash} WHERE code=#{userCode}")
    Boolean changeUserPasswordByCode(@Param("userCode") Integer userCode, @Param("passwordHash") String passwordHash);

    /**
     * 按编号修改密码并重置登录态
     * @param userCode 用户编号
     * @param passwordHash 新密码哈希
     * @param accountStatus 新账号状态
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET pass_word=#{passwordHash},token='',login_status='LOGOUT',account_status=#{accountStatus} WHERE code=#{userCode}")
    Boolean changePasswordAndRevokeLoginByCode(@Param("userCode") Integer userCode,
                                               @Param("passwordHash") String passwordHash,
                                               @Param("accountStatus") String accountStatus);

    /**
     * 管理员为用户解绑OTP
     * @param userName
     * @return
     */
    @Update("UPDATE user_tb SET auth_secret='',auth_status='UNBIND' WHERE user_name=#{name};")
    Boolean unbindUserOtp(@Param("name") String userName);

    /**
     * 清理OTP并回收网页登录态
     * @param userCode 用户编号
     * @param accountStatus 账号状态
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET auth_secret='',auth_status='UNBIND',token='',login_status='LOGOUT',account_status=#{accountStatus} WHERE code=#{userCode}")
    Boolean resetUserOtpByCode(@Param("userCode") Integer userCode,
                               @Param("accountStatus") String accountStatus);

    /**
     * 更新账号状态
     * @param userCode 用户编号
     * @param accountStatus 账号状态
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET account_status=#{accountStatus} WHERE code=#{userCode}")
    Boolean updateAccountStatus(@Param("userCode") Integer userCode, @Param("accountStatus") String accountStatus);

    /**
     * 回收网页登录态
     * @param userCode 用户编号
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET token='',login_status='LOGOUT' WHERE code=#{userCode}")
    Boolean revokeWebLoginByCode(@Param("userCode") Integer userCode);

    /**
     * 完成任务型OTP绑定
     * @param userCode 用户编号
     * @param accountStatus 新账号状态
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET auth_status='BIND',token='',login_status='LOGOUT',account_status=#{accountStatus} WHERE code=#{userCode}")
    Boolean completeSecurityTaskOtpByCode(@Param("userCode") Integer userCode,
                                          @Param("accountStatus") String accountStatus);

    /**
     * 首次设置访问令牌哈希
     * @param userCode 用户编号
     * @param accessTokenHash 访问令牌哈希
     * @param expireTime 过期时间
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET access_token_hash=#{accessTokenHash},access_token_expire_time=#{expireTime} WHERE code=#{userCode}")
    Boolean setAccessTokenHash(@Param("userCode") Integer userCode,
                               @Param("accessTokenHash") String accessTokenHash,
                               @Param("expireTime") Date expireTime);

    /**
     * 续期访问令牌
     * @param userCode 用户编号
     * @param expireTime 过期时间
     * @return 是否成功
     */
    @Update("UPDATE user_tb SET access_token_expire_time=#{expireTime} WHERE code=#{userCode}")
    Boolean renewAccessToken(@Param("userCode") Integer userCode,
                             @Param("expireTime") Date expireTime);
}
