package org.guohai.javasqlweb.controller;

import com.alibaba.druid.stat.DruidStatManagerFacade;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.config.AdminPageRequired;
import org.guohai.javasqlweb.service.BackstageService;
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

    @Autowired
    BackstageService backstageService;


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
}
