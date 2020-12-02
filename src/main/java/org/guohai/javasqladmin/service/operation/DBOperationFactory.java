package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ConnectConfigBean;

import java.sql.SQLException;

/**
 * 数据库操作工厂类
 * @author guohai
 */
public class DBOperationFactory {

    /**
     * MYSQL常量
     */
    private static final String MYSQL = "mysql";

    /**
     * MSSQL常量
     */
    private static final String MSSQL = "mssql";

    public static DBOperation createDbOperation(ConnectConfigBean conn) throws SQLException, ClassNotFoundException {
        DBOperation operation = null;
        if(MYSQL.equals(conn.getDbServerType())) {
            operation = new DBOperationMysql(conn);
        } else if(MSSQL.equals(conn.getDbServerType())) {
            operation = new DBOperationMssql(conn);
        }
        return operation;
    }
}
