package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.PermissionsDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 *
 */
@Service
public class PermissionsServiceImpl implements PermissionsService {

    @Autowired
    PermissionsDao permissionsDao;
    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(PermissionsServiceImpl.class);
    /**
     * 获取完整的用户组列表
     *
     * @return 返回 用户组列表
     */
    @Override
    public Result<List<UsergroupBean>> getAllUsergroup() {
        return new Result<>(true,"",permissionsDao.getUsergroups());
    }

    /**
     * 增加新的用户组
     *
     * @param groupName 组名
     * @param comment   组的备注
     * @param userList  用户列表
     * @return 返回请求结果
     */
    @Override
    public Result<String> addUsergroup(String groupName, String comment, List<UserBean> userList) {
        UsergroupBean userGroup = new UsergroupBean();
        userGroup.setComment(comment);
        userGroup.setGroupName(groupName);
        permissionsDao.addUsergroup(userGroup);
        LOG.info(userGroup.getCode().toString());
        return addUserToGroup(userGroup.getCode(), userList);
    }

    /**
     * 增加一批用户到指定组
     *
     * @param groupCode
     * @param userList
     * @return
     */
    @Override
    public Result<String> addUserToGroup(Integer groupCode, List<UserBean> userList) {

        for(Iterator<UserBean> it = userList.iterator();it.hasNext();){
            UserBean user = it.next();
            permissionsDao.addUserPermission(user.getCode(), groupCode);
        }
        return new Result<>(true,"success","");
    }

    /**
     * 从指定组移除所有用户
     *
     * @param groupCode 组编号
     * @return 返回请求结果
     */
    @Override
    public Result<String> delUserFromGroup(Integer groupCode) {
        permissionsDao.delUserPermissionByGroup(groupCode);
        return new Result<>(true,"success","操作成功");
    }

    /**
     * 删除用户组，同时删除在该组下所有用户
     *
     * @param groupCode 用户组编号
     * @return
     */
    @Override
    public Result<String> delUsergroup(Integer groupCode) {
        delUserFromGroup(groupCode);
        permissionsDao.delUserGroup(groupCode);
        return new Result<>(true,"success","操作成功");
    }

    /**
     * 增加用户的数据库权限
     *
     * @param groupCode  用户组
     * @param serverList 服务器列表
     * @return
     */
    @Override
    public Result<String> addDatabasePermission(Integer groupCode, List<ConnectConfigBean> serverList) {
        // 增加时先进行数据清空
        delDbPermissionByGroup(groupCode);
        for(Iterator<ConnectConfigBean> it = serverList.iterator();it.hasNext();){
            ConnectConfigBean server = it.next();
            permissionsDao.addDbPermission(server.getCode(), groupCode);
        }
        return  new Result<>(true,"success","操作成功");
    }

    /**
     * 根据组名删除权限
     *
     * @param groupCode
     * @return
     */
    @Override
    public Result<String> delDbPermissionByGroup(Integer groupCode) {
        return new Result<>(permissionsDao.delDbPermissions(groupCode),"", "");
    }

    /**
     * 根据组名获取服务器列表
     *
     * @param groupCode
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getServerConfigByGroup(Integer groupCode) {
        return new Result<>(true,"success", permissionsDao.getGroupPermissions(groupCode));
    }


    /**
     * 获取完整的权限列表
     *
     * @return
     */
    @Override
    public Result<List<DbPermissionBean>> getAllDbPerm() {

        return new Result<>(true,"success", permissionsDao.getDbPermissions());
    }
}
