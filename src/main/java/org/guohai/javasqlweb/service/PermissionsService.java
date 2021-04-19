package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UsergroupBean;

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
     * 从指定组移除一些用户
     * @param groupCode 组编号
     * @param userList 用户
     * @return 返回请求结果
     */
    Result<String> delUserFromGroup(Integer groupCode, List<UserBean> userList);

    /**
     * 删除用户组，同时删除在该组下所有用户
     * @param groupCode 用户组编号
     * @return
     */
    Result<String> delUsergroup(Integer groupCode);

}
