package org.guohai.javasqlweb.controller;

import com.alibaba.druid.stat.DruidStatManagerFacade;
import lombok.Data;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.config.AdminPageRequired;
import org.guohai.javasqlweb.service.BackstageService;
import org.guohai.javasqlweb.service.PermissionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public Result<String> unbindUserOtp(@RequestBody String userName){
        return backstageService.unbindUserOtp(userName);
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
        return permissionsService.getAllUsergroup();
    }

    @ResponseBody
    @RequestMapping(value = "/add_usergroups", method = RequestMethod.POST)
    public Result<String> addUsergroup(@RequestBody CreateUserGroupParam userGroup){

        return permissionsService.addUsergroup(userGroup.getGroupName(), userGroup.getGroupComment(),
                userGroup.getUserList());
    }
}

@Data
class CreateUserGroupParam{
    private String groupName;
    private String groupComment;
    private List<UserBean> userList;
}
