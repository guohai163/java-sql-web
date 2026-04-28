package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.DashboardResponse;
import org.guohai.javasqlweb.beans.LinkIssueResult;
import org.guohai.javasqlweb.beans.PoolStatBean;
import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.QueryLogCursorResponse;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.ServerDatabaseSyncResult;
import org.guohai.javasqlweb.beans.TargetPoolStatBean;
import org.guohai.javasqlweb.beans.TargetSessionStatBean;
import org.guohai.javasqlweb.beans.UserBean;
import org.springframework.web.bind.annotation.RequestBody;

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
    Result<QueryLogCursorResponse> getQueryLog(Integer pageSize, Integer cursorCode, String direction);

    /**
     * 获取连接表
     * @return
     */
    Result<List<ConnectConfigBean>> getConnData();

    /**
     * 获取连接表（支持关键字、类型、库名筛选）
     * @param keyword 服务器名关键字（包含匹配）
     * @param serverType 服务器类型
     * @param dbName 数据库名（全等匹配）
     * @return
     */
    Result<List<ConnectConfigBean>> getConnData(String keyword, String serverType, String dbName);

    /**
     * 同步所有实例的库名快照
     * @return 同步统计结果
     */
    Result<ServerDatabaseSyncResult> syncServerDatabases();

    /**
     * 获取连接池摘要
     * @return 连接池信息
     */
    Result<List<PoolStatBean>> getPoolStats();

    /**
     * 获取动态目标库连接池运行时快照
     * @return
     */
    Result<List<TargetPoolStatBean>> getTargetPoolStats();

    /**
     * 获取指定目标库的动态连接会话明细
     * @param code 服务器编号
     * @return 会话明细
     */
    Result<List<TargetSessionStatBean>> getTargetPoolSessions(Integer code);

    /**
     * 测试数据库连接性
     * @param server 服务器信息
     * @return
     */
    Result<String> testServerConnect(@RequestBody ConnectConfigBean server);

    /**
     * 测试已保存服务器配置的数据库连接性
     * @param code 服务器编号
     * @return
     */
    Result<String> testSavedServerConnect(Integer code);
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
     * 获取首页驾驶舱数据
     * @param range 时间范围
     * @param grain 粒度
     * @param userLimit 用户排行数量
     * @param dbLimit 数据库排行数量
     * @param tableLimit 表排行数量
     * @param recentLimit 最近查询数量
     * @return 驾驶舱数据
     */
    Result<DashboardResponse> getDashboard(String range,
                                           String grain,
                                           Integer userLimit,
                                           Integer dbLimit,
                                           Integer tableLimit,
                                           Integer recentLimit);

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
    Result<LinkIssueResult> addNewUser(String token, UserBean user);

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
     * 重置指定服务器的动态连接池与冷却状态
     * @param code
     * @return
     */
    Result<String> resetServer(Integer code);

    /**
     * 通过有效token修改用户密码
     * @param token
     * @param newPass
     * @return
     */
    Result<LinkIssueResult> reissueActivationLink(String token, String userName);

    /**
     * 管理员为用户发起密码重置
     * @param token 管理员登录态
     * @param userName 用户名
     * @return 链接签发结果
     */
    Result<LinkIssueResult> resetUserPassword(String token, String userName);

    /**
     * 管理员为用户发起OTP重绑
     * @param token 管理员登录态
     * @param userName 用户名
     * @return 链接签发结果
     */
    Result<LinkIssueResult> resetUserOtp(String token, String userName);

    /**
     * 更新服务器数据
     * @param server
     * @return
     */
    Result<String> updateServerData(ConnectConfigBean server);

}
