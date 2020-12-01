package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.DatabaseNameBean;
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
}
