package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ColumnsNameBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;
import org.guohai.javasqladmin.beans.TableIndexesBean;
import org.guohai.javasqladmin.beans.TablesNameBean;

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
    List<DatabaseNameBean> getDBList() throws SQLException, ClassNotFoundException;

    /**
     * 获得实例指定库的所有表名
     * @param dbName
     * @return
     */
    List<TablesNameBean> getTableList(String dbName) throws SQLException;

    /**
     * 获取所有列名
     * @param dbName
     * @param tableName
     * @return
     */
    List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException;

    /**
     * 获取所有的索引数据
     * @param dbName
     * @param tableName
     * @return
     */
    List<TableIndexesBean> getIndexesList(String dbName, String tableName) throws SQLException;
}
