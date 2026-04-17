package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;
import java.util.List;
import java.util.Map;

/**
 * 数据操作基础服务
 * @author guohai
 */
public interface BaseDataService {

    /**
     * 返回完整的数据
     * @return
     */
    Result<List<ConnectConfigBean>> getAllDataConnect();

    /**
     * 获取指定服务器信息
     * @param serverCode
     * @param user 已认证用户
     * @return
     */
    Result<ConnectConfigBean> getServerInfo(Integer serverCode, UserBean user);

    /**
     * 获得指定DB服务器的库名列表
     * @param serverCode
     * @param user 已认证用户
     * @return
     */
    Result<List<DatabaseNameBean>> getDbName(Integer serverCode, UserBean user);

    /**
     * 获得指定库的所有表名
     * @param serverCode
     * @param dbName
     * @param user 已认证用户
     * @return
     */
    Result<List<TablesNameBean>> getTableList(Integer serverCode, String dbName, UserBean user);

    /**
     * 获得指定表的所有列
     * @param serverCode
     * @param dbName
     * @param tableName
     * @param user 已认证用户
     * @return
     */
    Result<List<ColumnsNameBean>> getColumnList(Integer serverCode, String dbName, String tableName, UserBean user);

    /**
     * 或者指定库的 表列集合
     * @param serverCode
     * @param dbName
     * @param user 已认证用户
     * @return
     */
    Result<Map<String ,String[]>> getTableColumn(Integer serverCode, String dbName, UserBean user);

    /**
     * 获得指定表的所有索引
     * @param serverCode
     * @param dbName
     * @param tableName
     * @param user 已认证用户
     * @return
     */
    Result<List<TableIndexesBean>> getTableIndexes(Integer serverCode, String dbName, String tableName, UserBean user);

    /**
     * 获取指定库的视图列表
     * @param serverCode
     * @param dbName
     * @param user 已认证用户
     * @return
     */
    Result<List<ViewNameBean>> getViewList(Integer serverCode, String dbName, UserBean user);

    /**
     * 获取指定视图的创建语句
     * @param serverCode
     * @param dbName
     * @param viewName
     * @param user 已认证用户
     * @return
     */
    Result<ViewNameBean> getViewByName(Integer serverCode, String dbName, String viewName, UserBean user);

    /**
     * 获取指定库的存储过程列表,只含名字
     * @param serverCode
     * @param dbName
     * @param user 已认证用户
     * @return
     */
    Result<List<StoredProceduresBean>> getSpList(Integer serverCode, String dbName, UserBean user);

    /**
     * 通过存储过程名获取存储过程内容
     * @param serverCode
     * @param dbName
     * @param spName
     * @param user 已认证用户
     * @return
     */
    Result<StoredProceduresBean> getSpByName(Integer serverCode, String dbName, String spName, UserBean user);

    /**
     * 执行查询语句
     * @param serverCode
     * @param dbName
     * @param sql
     * @param user
     * @param userIp
     * @return
     */
    Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql, UserBean user, String userIp);

    /**
     * 获取工作台 dashboard
     * @param serverCode 服务器编号
     * @param dbName 数据库名
     * @param user 已认证用户
     * @param forceRefresh 是否强制刷新
     * @return dashboard 数据
     */
    Result<WorkbenchDashboardResponse> getWorkbenchDashboard(Integer serverCode,
                                                            String dbName,
                                                            UserBean user,
                                                            boolean forceRefresh);

    /**
     * 检查所有服务器的健康状态
     * @return
     */
    Result<String> serverHealth();

    /**
     * 获取数据库分组
     * @param user 已认证用户
     * @return
     */
    Result<List<String>> getDbGroup(UserBean user);

    /**
     * 获取指定用户可以看的列表
     * @param user 已认证用户
     * @return
     */
    Result<List<ConnectConfigBean>> getHavaPermConn(UserBean user);

    /**
     * 获取完整的向导
     * @return
     */
    Result<List<SqlGuidBean>> getAllGuid();


}
