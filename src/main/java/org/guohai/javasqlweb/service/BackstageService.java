package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;

import java.util.List;
import java.util.Map;

/**
 * 后台服务
 * @author guohai
 */
public interface BackstageService {

    /**
     * 获取查询日志
     * @return
     */
    Result<List<QueryLogBean>> getQueryLog();

    /**
     * 获取连接表
     * @return
     */
    Result<List<ConnectConfigBean>> getConnData();

    /**
     * 增加服务器
     * @param server
     * @return
     */
    Result<String> addConnServer(ConnectConfigBean server);

    /**
     * 获取站点基础数据
     * @return
     */
    Result<Map> getSiteBaseData();

    /**
     * 获取完整用户数据
     * @return
     */
    Result<List<UserBean>> getUserData();


    /**
     * 增加新用户
     * @param user 用户
     * @return
     */
    Result<String> addNewUser(UserBean user);

    /**
     * 删除指定用户
     * @param userName
     * @return
     */
    Result<String> delUser(String userName);

    /**
     * 删除指定服务器连接
     * @param code
     * @return
     */
    Result<String> delServer(Integer code);

    /**
     * 通过有效token修改用户密码
     * @param token
     * @param newPass
     * @return
     */
    Result<String> changeUserPass(String token, String newPass);
}
