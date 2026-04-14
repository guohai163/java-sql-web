package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.ConnectConfigBean;

/**
 * 数据库操作工厂类
 * @author guohai
 */
public class DbOperationFactory {

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
    private static final String POSTGRESQL = "postgresql";

    private static final String CLICKHOUCE = "clickhouce";


    public static DbOperation createDbOperation(ConnectConfigBean conn) throws Exception {
        DbOperation operation = null;
        if(MYSQL.equals(conn.getDbServerType())) {
            operation = new DbOperationMysqlDruid(conn);
        } else if(MSSQL.equals(conn.getDbServerType())) {
            operation = new DbOperationMssqlDruid(conn);

        }else if(POSTGRESQL.equals(conn.getDbServerType())) {
            operation = new DbOperationPostgresqlDruid(conn);
        } else if (CLICKHOUCE.equals(conn.getDbServerType())) {
            operation = new DbOperationClickHouse(conn);
        }
        return operation;
    }
}
