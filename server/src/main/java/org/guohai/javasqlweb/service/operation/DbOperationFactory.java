package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.util.DbServerTypeUtils;

/**
 * 数据库操作工厂类
 * @author guohai
 */
public class DbOperationFactory {

    public static DbOperation createDbOperation(ConnectConfigBean conn) throws Exception {
        String dbType = DbServerTypeUtils.normalize(conn.getDbServerType());
        DbOperation operation = null;
        if(DbServerTypeUtils.MYSQL.equals(dbType) || DbServerTypeUtils.MARIADB.equals(dbType)) {
            operation = new DbOperationMysqlDruid(conn);
        } else if(DbServerTypeUtils.MSSQL.equals(dbType)) {
            operation = new DbOperationMssqlDruid(conn);
        }else if(DbServerTypeUtils.POSTGRESQL.equals(dbType)) {
            operation = new DbOperationPostgresqlDruid(conn);
        } else if (DbServerTypeUtils.CLICKHOUSE.equals(dbType)) {
            operation = new DbOperationClickHouse(conn);
        }
        return operation;
    }
}
