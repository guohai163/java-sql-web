package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.*;

import java.util.List;

public interface BaseDataService {

    /**
     * 返回完整的数据
     * @return
     */
    Result<List<ConnectConfigBean>> getAllDataConnect();

    /**
     * 获得指定DB服务器的库名列表
     * @param dbCode
     * @return
     */
    Result<List<DatabaseNameBean>> getDbName(Integer dbCode);

    /**
     * 获得指定库的所有表名
     * @param dbCode
     * @param dbName
     * @return
     */
    Result<List<TablesNameBean>> getTableList(Integer dbCode, String dbName);

    /**
     *
     * @param dbCode
     * @param dbName
     * @param tableName
     * @return
     */
    Result<List<ColumnsNameBean>> getColumnList(Integer dbCode, String dbName, String tableName);
}
