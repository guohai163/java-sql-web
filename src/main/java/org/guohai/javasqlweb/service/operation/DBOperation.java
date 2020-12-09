package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.*;

import java.sql.SQLException;
import java.util.List;

/**
 * 单例工厂的抽象接口
 */
public interface DBOperation {

    /**
     * 获得实例服务器库列表
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    List<DatabaseNameBean> getDbList() throws SQLException, ClassNotFoundException;

    /**
     * 获得实例指定库的所有表名
     * @param dbName 库名
     * @return 返回集合
     * @throws SQLException 抛出异常
     */
    List<TablesNameBean> getTableList(String dbName) throws SQLException;

    /**
     * 获取所有列名
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
     */
    List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException;

    /**
     * 获取所有的索引数据
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
     */
    List<TableIndexesBean> getIndexesList(String dbName, String tableName) throws SQLException;

    /**
     * 获取指定库的所有存储过程列表
     * @param dbName
     * @return
     * @throws SQLException
     */
    List<StoredProceduresBean> getStoredProceduresList(String dbName) throws SQLException;

    /**
     * 获取指定存储过程内容
     * @param dbName
     * @param spName
     * @return
     * @throws SQLException
     */
    StoredProceduresBean getStoredProcedure(String dbName, String spName) throws SQLException;
    /**
     * 执行查询的SQL
     * @param dbName
     * @param sql
     * @return
     * @throws SQLException 抛出异常
     */
    Object queryDatabaseBySql(String dbName, String sql) throws SQLException;
}
