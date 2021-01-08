package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.ConnectConfigBean;

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

    /**
     *
     */
    private static final String ORACLE = "oracle";


    public static DBOperation createDbOperation(ConnectConfigBean conn) throws Exception {
        DBOperation operation = null;
        if(MYSQL.equals(conn.getDbServerType())) {
            operation = new DBOperationMysql(conn);
        } else if(MSSQL.equals(conn.getDbServerType())) {
            operation = new DBOperationMssqlDruid(conn);
        }else if(ORACLE.equals(conn.getDbServerType())) {
            operation = new DBOperationOracle(conn);
        }
        return operation;
    }
}
