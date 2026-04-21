package org.guohai.javasqlweb.controller;

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
    @RequestMapping(value = "/querylog", method = RequestMethod.GET)
    public Result<QueryLogCursorResponse> getQueryLog(
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursorCode", required = false) Integer cursorCode,
            @RequestParam(value = "direction", defaultValue = "older") String direction){
        return backstageService.getQueryLog(pageSize, cursorCode, direction);
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
     * 获取连接池状态摘要
     * @return
     */
    @RequestMapping(value = "/druid/stat")
    @ResponseBody
    public Result<List<PoolStatBean>> druidStat(){
        return backstageService.getPoolStats();
    }

    @RequestMapping(value = "/server-runtime", method = RequestMethod.GET)
    @ResponseBody
    public Result<List<TargetPoolStatBean>> serverRuntime() {
        return backstageService.getTargetPoolStats();
    }

    @RequestMapping(value = "/server-runtime/{code}/sessions", method = RequestMethod.GET)
    @ResponseBody
    public Result<List<TargetSessionStatBean>> serverRuntimeSessions(@PathVariable("code") Integer code) {
        return backstageService.getTargetPoolSessions(code);
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
     * 获取首页驾驶舱数据
     * @param range 时间范围
     * @param grain 粒度
     * @param userLimit 用户排行数量
     * @param dbLimit 数据库排行数量
     * @param tableLimit 表排行数量
     * @param recentLimit 最近查询数量
     * @return 驾驶舱数据
     */
    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    @ResponseBody
    public Result<DashboardResponse> getDashboard(
            @RequestParam(value = "range", defaultValue = "24h") String range,
            @RequestParam(value = "grain", defaultValue = "hour") String grain,
            @RequestParam(value = "userLimit", defaultValue = "10") Integer userLimit,
            @RequestParam(value = "dbLimit", defaultValue = "5") Integer dbLimit,
            @RequestParam(value = "tableLimit", defaultValue = "10") Integer tableLimit,
            @RequestParam(value = "recentLimit", defaultValue = "10") Integer recentLimit) {
        return backstageService.getDashboard(range, grain, userLimit, dbLimit, tableLimit, recentLimit);
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
    public Result<LinkIssueResult> addNewUser(@RequestHeader(value = "User-Token", required = false) String token,
                                              @RequestBody UserBean user){
        return backstageService.addNewUser(token, user);
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
     * 测试服务器连接性
     * @param server
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/testserver", method = RequestMethod.POST)
    public Result<String> testServerConnect(@RequestBody ConnectConfigBean server){
        return backstageService.testServerConnect(server);
    }

    /**
     * 测试已保存的服务器连接性
     * @param code 服务器编号
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/testserver/{code}", method = RequestMethod.POST)
    public Result<String> testSavedServerConnect(@PathVariable("code") Integer code){
        return backstageService.testSavedServerConnect(code);
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

    @ResponseBody
    @RequestMapping(value = "/resetserver/{code}", method = RequestMethod.POST)
    public Result<String> resetServer(@PathVariable("code") Integer code){
        return backstageService.resetServer(code);
    }

    /**
     * 重发用户激活链接
     * @param token 管理员登录态
     * @param user 用户
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/reissue_activation_link", method = RequestMethod.POST)
    public Result<LinkIssueResult> reissueActivationLink(
            @RequestHeader(value = "User-Token", required = false) String token,
            @RequestBody UserBean user){
        return backstageService.reissueActivationLink(token, user.getUserName());
    }

    /**
     * 为指定用户生成密码重置链接
     * @param token 管理员登录态
     * @param user 用户名
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/reset_user_password", method = RequestMethod.POST)
    public Result<LinkIssueResult> resetUserPassword(
            @RequestHeader(value = "User-Token", required = false) String token,
            @RequestBody UserBean user){
        return backstageService.resetUserPassword(token, user.getUserName());
    }

    /**
     * 为指定用户生成OTP重绑链接
     * @param token 管理员登录态
     * @param user 用户名
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/reset_user_otp", method = RequestMethod.POST)
    public Result<LinkIssueResult> resetUserOtp(
            @RequestHeader(value = "User-Token", required = false) String token,
            @RequestBody UserBean user){
        return backstageService.resetUserOtp(token, user.getUserName());
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
