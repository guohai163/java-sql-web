package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.*;

import java.sql.SQLException;
import java.util.List;

public class DBOperationMysql implements DBOperation {

    private ConnectConfigBean connectConfigBean;
    DBOperationMysql(ConnectConfigBean conn){
        connectConfigBean = conn;
    }

    @Override
    public List<DatabaseNameBean> getDbList() {
        return null;
    }

    /**
     * 获得实例指定库的所有表名
     *
     * @param dbName
     * @return
     */
    @Override
    public List<TablesNameBean> getTableList(String dbName) throws SQLException {
        return null;
    }

    /**
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        return null;
    }

    /**
     * 获取所有的索引数据
     *
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public List<TableIndexesBean> getIndexesList(String dbName, String tableName) throws SQLException {
        return null;
    }

    /**
     * 执行查询的SQL
     *
     * @param dbName
     * @param sql
     * @return
     */
    @Override
    public Object queryDatabaseBySql(String dbName, String sql) throws SQLException {
        return null;
    }
}
