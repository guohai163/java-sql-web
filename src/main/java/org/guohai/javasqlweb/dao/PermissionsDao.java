package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqlweb.beans.UsergroupBean;
import org.springframework.stereotype.Repository;

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

}