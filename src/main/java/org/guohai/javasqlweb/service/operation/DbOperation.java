package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 单例工厂的抽象接口
 * @author guohai
 */
public interface DbOperation {

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
     * 取回视图列表
     * @param dbName
     * @return
     * @throws SQLException
     */
    List<ViewNameBean> getViewsList(String dbName) throws  SQLException;

    /**
     * 获取视图详细信息
     * @param dbName
     * @param viewName
     * @return
     * @throws SQLException
     */
    ViewNameBean getView(String dbName, String viewName) throws  SQLException;
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
     * @param limit
     * @return
     * @throws SQLException 抛出异常
     */
     Object[] queryDatabaseBySql(String dbName, String sql, Integer limit) throws SQLException;

    /**
     * 返回一个数据库的所有表和列集合
     * @param dbName
     * @return
     * @throws SQLException
     */
     Map<String, String[]> getTablesColumnsMap(String dbName) throws SQLException;

    /**
     * 服务器连接状态健康检查
     * @return
     * @throws SQLException
     */
    Boolean serverHealth() throws SQLException;
}
