package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ConnectConfigBean;

import java.sql.SQLException;

/**
 * 数据库操作工厂类
 * @author guohai
 */
public class DBOperationFactory {

    public static DBOperation createDBOperation(ConnectConfigBean conn) throws SQLException, ClassNotFoundException {
        DBOperation operation = null;
        if("mysql".equals(conn.getDbServerType())) {
            operation = new DBOperationMysql(conn);
        } else if("mssql".equals(conn.getDbServerType())) {
            operation = new DBOperationMssql(conn);
        }
        return operation;
    }
}
