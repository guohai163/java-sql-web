package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.*;

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
     * 执行查询语句
     * @param serverCode
     * @param dbName
     * @param sql
     * @return
     */
    Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql);
}
