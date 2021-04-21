package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.*;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.DbPermissionBean;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UsergroupBean;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;

/**
 * 权限管理操作DAO
 * @author guohai
 * @date 2021-4-16
 */
@Repository
public interface PermissionsDao {

    /**
     * 获取全部的组列表
     * @return
     */
    @Select("SELECT * FROM usergroup")
    List<UsergroupBean> getUsergroups();

    /**
     * 获取组数据，显示用
     * @return
     */
    @Select("SELECT a.code,group_name,comment,COALESCE(group_concat(distinct user_name),'') as user_array \n" +
            "FROM usergroup a\n" +
            "left join user_permissions b on a.code=b.group_code\n" +
            "left join user_tb c on b.user_code=c.code\n" +
            "group by a.code;")
    List<UsergroupBean> getUserGroupInUser();
    /**
     * 创建新的用户组
     * @param userGroup
     */
    @Insert("INSERT INTO usergroup (`group_name`,`comment`) VALUES(#{groupName}, #{comment}) ")
    @Options(useGeneratedKeys = true, keyProperty = "code", keyColumn = "code")
    void addUsergroup(UsergroupBean userGroup);

    /**
     * 绑定人与用户组
     * @param userCode
     * @param groupCode
     * @return
     */
    @Insert("INSERT INTO `user_permissions`(`user_code`,`group_code`)VALUES(#{userCode},#{groupCode});")
    Boolean addUserPermission(Integer userCode, Integer groupCode);

    /**
     * 通过用户组编号，删除用户权限
     * @param groupCode
     * @return
     */
    @Delete("DELETE FROM user_permissions WHERE group_code=#{groupCode};")
    Boolean delUserPermissionByGroup(Integer groupCode);

    /**
     * 通过用户组编号，删除用户组
     * @param groupCode
     * @return
     */
    @Delete("DELETE FROM usergroup WHERE code=#{groupCode};")
    Boolean delUserGroup(Integer groupCode);

    /**
     * 增加数据库权限
     * @param dbCode
     * @param groupCode
     * @return
     */
    @Insert("INSERT INTO `db_permissions`(`db_code`,`group_code`)VALUES(#{dbCode}, #{groupCode});")
    Boolean addDbPermission(Integer dbCode, Integer groupCode);


    /**
     * 获取权限 列表，仅用于展示
     * @return
     */
    @Select("SELECT group_code,group_name,GROUP_CONCAT(DISTINCT db_server_name) as server_list FROM db_permissions a " +
            "join usergroup b on a.group_code=b.code " +
            "join db_connect_config_tb c on a.db_code=c.code " +
            " GROUP BY group_code,group_name")
    List<DbPermissionBean> getDbPermissions();

    /**
     * 按组名删除权限分配
     * @param groupCode
     * @return
     */
    @Delete("DELETE FROM `db_permissions` WHERE group_code=#{groupName}")
    Boolean delDbPermissions(Integer groupCode);

    /**
     * 根据组名获取对应的服务器列表
     * @param groupCode
     * @return
     */
    @Select("SELECT code,db_server_name,db_server_type,db_group FROM db_connect_config_tb WHERE code in " +
            "(SELECT db_code FROM `db_permissions` WHERE group_code=#{groupCode})")
    List<ConnectConfigBean> getGroupPermissions(Integer groupCode);

    /**
     * 返回组内所有用户数据
     * @param groupCode
     * @return
     */
    @Select("select code,user_name from user_tb where code in " +
            "(select user_code from user_permissions where group_code=#{groupCode})")
    List<UserBean> getGroupUser(Integer groupCode);

    @Update("UPDATE `usergroup` SET `group_name` = #{groupName},`comment` = #{comment} WHERE `code` = #{groupCode};")
    Boolean setUserGroup(Integer groupCode, String groupName, String comment);

}
