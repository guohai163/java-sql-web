package org.guohai.javasqlweb.service;

import org.apache.ibatis.annotations.Insert;
import org.guohai.javasqlweb.beans.*;

import java.util.HashMap;
import java.util.List;

/**
 * 权限管理
 * @author guohai
 * @date 2021-4-15
 */
public interface PermissionsService {

    /**
     * 获取完整的用户组列表
     * @return 返回 用户组列表
     */
    Result<List<UsergroupBean>> getAllUsergroup();

    /**
     * 获取含用户列表的组数据
     * @return
     */
    Result<List<UsergroupBean>> getGroupDataInUser();

    /**
     * 增加新的用户组
     * @param groupName 组名
     * @param comment 组的备注
     * @param userList 用户列表
     * @return 返回请求结果
     */
    Result<String> addUsergroup(String groupName, String comment, List<UserBean> userList);

    /**
     * 增加一批用户到指定组
     * @param groupCode
     * @param userList
     * @return
     */
    Result<String> addUserToGroup(Integer groupCode, List<UserBean> userList);

    /**
     * 从指定组移除所有用户
     * @param groupCode 组编号
     * @return 返回请求结果
     */
    Result<String> delUserFromGroup(Integer groupCode);

    /**
     * 删除用户组，同时删除在该组下所有用户
     * @param groupCode 用户组编号
     * @return
     */
    Result<String> delUsergroup(Integer groupCode);

    /**
     * 增加用户的数据库权限
     * @param groupCode 用户组
     * @param serverList 服务器列表
     * @return
     */
    Result<String> addDatabasePermission(Integer groupCode, List<ConnectConfigBean> serverList);

    /**
     * 根据组名删除权限
     * @param groupCode
     * @return
     */
    Result<String> delDbPermissionByGroup(Integer groupCode);

    /**
     * 根据组名获取服务器列表
     * @param groupCode
     * @return
     */
    Result<List<ConnectConfigBean>> getServerConfigByGroup(Integer groupCode);

    /**
     * 获取完整的权限列表
     * @return
     */
    Result<List<DbPermissionBean>> getAllDbPerm();

    /**
     * 获取组内用户列表
     * @param groupCode
     * @return
     */
    Result<List<UserBean>> getGroupUser(Integer groupCode);

    /**
     * 更新用户组数据
     * @param usergroup
     * @param userList
     * @return
     */
    Result<String> setUserGroupData(UsergroupBean usergroup, List<UserBean> userList);


}
