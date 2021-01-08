package org.guohai.javasqlweb.dao;

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import org.apache.ibatis.annotations.*;
import org.guohai.javasqlweb.beans.UserBean;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 管理操作类
 * @author guohai
 */
@Repository
public interface UserManageDao {

    /**
     * 查询指定用户名密码的用户数据是否存在
     * @param name
     * @param pass
     * @return
     */
    @Select("SELECT user_name,auth_status FROM user_tb WHERE user_name=#{name} AND pass_word=md5(CONCAT(md5(#{pass}),'jsa'))")
    UserBean checkUserNamePass(@Param("name") String name, @Param("pass") String pass);

    /**
     * 通过用户名检查用户是否存在
     * @param name
     * @return
     */
    @Select("SELECT user_name FROM user_tb WHERE user_name=#{name}")
    UserBean getUserByName(@Param("name") String name);
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
    @Select("SELECT user_name,auth_status,auth_secret,login_status FROM user_tb WHERE token=#{token}")
    UserBean getUserByToken(@Param("token") String token);


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
    @Select("SELECT code,user_name,auth_status FROM user_tb;")
    List<UserBean> getUserList();

    @Insert("INSERT INTO `user_tb` (`user_name`,`pass_word`,`token`) VALUES" +
            "(#{name},md5(CONCAT(md5(#{pass}),'jsa')),'');")
    Boolean addNewUser(@Param("name") String userName,@Param("pass") String userPass);

    /**
     * 删除指定用户
     * @param userName
     * @return
     */
    @Delete("DELETE FROM `user_tb` WHERE user_name=#{name};")
    Boolean delUser(@Param("name") String userName);

    /**
     * 通过有效Token直接修改用户密码
     * @param token
     * @param newPass
     * @return
     */
    @Update("UPDATE user_tb SET pass_word=md5(CONCAT(md5(#{newpass}),'jsa')) WHERE token=#{token}")
    Boolean changeUserPassword(@Param("token") String token, @Param("newpass") String newPass);
}
