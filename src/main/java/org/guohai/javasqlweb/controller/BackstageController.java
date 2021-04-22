package org.guohai.javasqlweb.controller;

import com.alibaba.druid.stat.DruidStatManagerFacade;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.config.AdminPageRequired;
import org.guohai.javasqlweb.service.BackstageService;
import org.guohai.javasqlweb.service.PermissionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 后台控制器类
 * @author guohai
 * @date 2021-1-1
 */
@AdminPageRequired
@RestController
@RequestMapping(value = "/api/backstage")
@CrossOrigin
public class BackstageController {

    private static final Logger LOG  = LoggerFactory.getLogger(BackstageController.class);

    @Autowired
    BackstageService backstageService;

    @Autowired
    PermissionsService permissionsService;

    /**
     * 日志查询
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/querylog")
    public Result<List<QueryLogBean>> getQueryLog(){
        return backstageService.getQueryLog();
    }

    /**
     * 连接列表查询
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/connlist")
    public Result<List<ConnectConfigBean>> getConnData(){
        return backstageService.getConnData();
    }

    /**
     * 获取druid状态
     * @return
     */
    @RequestMapping(value = "/druid/stat")
    @ResponseBody
    public Object druidStat(){
        return DruidStatManagerFacade.getInstance().getDataSourceStatDataList();
    }

    /**
     * 返回基础信息
     * @return
     */
    @RequestMapping(value = "/base")
    @ResponseBody
    public Result<Map> getBaseData(){
        return backstageService.getSiteBaseData();
    }

    /**
     * 查询用户列表
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/userlist")
    public Result<List<UserBean>> getUserData(){
        return backstageService.getUserData();
    }

    /**
     * 增加新用户
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/adduser", method = RequestMethod.POST)
    public Result<String> addNewUser(@RequestBody UserBean user){
        return backstageService.addNewUser(user);
    }

    /**
     * 删除用户
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/deluser", method = RequestMethod.POST)
    public Result<String> delUser(@RequestBody UserBean user){
        return backstageService.delUser(user.getUserName());
    }

    /**
     * 增加服务器
     * @param server
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/addserver", method = RequestMethod.POST)
    public Result<String> addServer(@RequestBody ConnectConfigBean server){
        return backstageService.addConnServer(server);
    }

    /**
     * 删除服务器
     * @param code
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/delserver", method = RequestMethod.POST)
    public Result<String> delServer(@RequestBody Integer code){
        return backstageService.delServer(code);
    }

    /**
     * 修改用户密码
     * @param token
     * @param newPass
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/change_new_pass", method = RequestMethod.POST)
    public Result<String> changeNewPass(@RequestHeader(value = "User-Token", required =  false) String token,
                                        @RequestBody String newPass){
        LOG.debug(String.format("将为用户token为%s的修改密码", token));
        return backstageService.changeUserPass(token,newPass);
    }

    /**
     * 为指定用户解绑OTP
     * @param userName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/unbind_opt", method = RequestMethod.POST)
    public Result<String> unbindUserOtp(@RequestBody UserBean user){
        return backstageService.unbindUserOtp(user.getUserName());
    }

    /**
     * 更新服务器信息
     * @param server
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/update_server", method = RequestMethod.POST)
    public Result<String> updateServer(@RequestBody ConnectConfigBean server){
        return backstageService.updateServerData(server);
    }

    /**
     * 获取用户组
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/usergroups", method = RequestMethod.GET)
    public Result<List<UsergroupBean>> getAllUsergroup(){
        return permissionsService.getGroupDataInUser();
    }



    /**
     * 为用户组绑定权限
     * @param perm
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/add_permission", method = RequestMethod.POST)
    public Result<String> addPermission(@RequestBody CreatePermissionParam perm){
        return permissionsService.addDatabasePermission(perm.getGroupCode(),perm.getServerList());
    }

    /**
     * 获取已经授权的权限列表
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/db_perm", method = RequestMethod.GET)
    public Result<List<DbPermissionBean>> getAllDbPerm(){
        return permissionsService.getAllDbPerm();
    }

    /**
     * 获取指定用户组的数据库列表
     * @param groupCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/server_list/{group_code}", method = RequestMethod.GET)
    public Result<List<ConnectConfigBean>> getServerConfigByGroup(@PathVariable("group_code") Integer groupCode){
        return permissionsService.getServerConfigByGroup(groupCode);
    }

    @ResponseBody
    @RequestMapping(value = "/db_perm/{group_code}", method = RequestMethod.DELETE)
    public Result<String> delDbPermission(@PathVariable("group_code") Integer groupCode) {
        return permissionsService.delDbPermissionByGroup(groupCode);
    }

    //region 权限管理

    //endregion


    //region 用户组操作
    /**
     * 获取用户组内，用户列表
     * @param groupCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/group_user/{group_code}", method = RequestMethod.GET)
    public Result<List<UserBean>> getGroupUser(@PathVariable("group_code") Integer groupCode) {
        return permissionsService.getGroupUser(groupCode);
    }

    /**
     * 删除用户组
     * @param groupCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/usergroup/{group_code}", method = RequestMethod.DELETE)
    public Result<String> delUserGroup(@PathVariable("group_code") Integer groupCode){
        return permissionsService.delUsergroup(groupCode);
    }

    /**
     * 增加用户组
     * @param userGroup
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/add_usergroups", method = RequestMethod.POST)
    public Result<String> addUsergroup(@RequestBody CreateUserGroupParam userGroup){

        return permissionsService.addUsergroup(userGroup.getGroupName(), userGroup.getComment(),
                userGroup.getUserList());
    }

    /**
     * 更新用户组数据
     * @param userGroup
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/set_group_data", method = RequestMethod.PUT)
    public Result<String> setUsergroupData(@RequestBody CreateUserGroupParam userGroup){
        return permissionsService.setUserGroupData(userGroup, userGroup.getUserList());
    }
    //endregion
}

/**
 * 创建用户组接口参数
 */
@Data
@EqualsAndHashCode(callSuper=false)
class CreateUserGroupParam extends UsergroupBean {
    private List<UserBean> userList;
}

/**
 * 创建权限组参数
 */
@Data
class CreatePermissionParam{
    private Integer groupCode;
    private List<ConnectConfigBean> serverList;
}
