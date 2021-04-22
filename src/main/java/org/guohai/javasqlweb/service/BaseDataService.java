package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;

import java.util.List;

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
     * @return
     */
    Result<ConnectConfigBean> getServerInfo(Integer serverCode);

    /**
     * 获得指定DB服务器的库名列表
     * @param serverCode
     * @return
     */
    Result<List<DatabaseNameBean>> getDbName(Integer serverCode);

    /**
     * 获得指定库的所有表名
     * @param serverCode
     * @param dbName
     * @return
     */
    Result<List<TablesNameBean>> getTableList(Integer serverCode, String dbName);

    /**
     * 获得指定表的所有列
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    Result<List<ColumnsNameBean>> getColumnList(Integer serverCode, String dbName, String tableName);

    /**
     * 获得指定表的所有索引
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    Result<List<TableIndexesBean>> getTableIndexes(Integer serverCode, String dbName, String tableName);

    /**
     * 获取指定库的存储过程列表,只含名字
     * @param serverCode
     * @param dbName
     * @return
     */
    Result<List<StoredProceduresBean>> getSpList(Integer serverCode, String dbName);

    /**
     * 通过存储过程名获取存储过程内容
     * @param serverCode
     * @param dbName
     * @param spName
     * @return
     */
    Result<StoredProceduresBean> getSpByName(Integer serverCode, String dbName, String spName);

    /**
     * 执行查询语句
     * @param serverCode
     * @param dbName
     * @param sql
     * @param token
     * @param userIp
     * @return
     */
    Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql, String token, String userIp);

    /**
     * 检查所有服务器的健康状态
     * @return
     */
    Result<String> serverHealth();

    /**
     * 获取数据库分组
     * @param token 用户令牌
     * @return
     */
    Result<List<String>> getDbGroup(String token);

    /**
     * 获取指定用户可以看的列表
     * @param token
     * @return
     */
    Result<List<ConnectConfigBean>> getHavaPermConn(String token);
}
