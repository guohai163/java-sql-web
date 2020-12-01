package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ConnectConfigBean;

public class DBOperationFactory {

    public static DBOperation createDBOperation(ConnectConfigBean conn){
        DBOperation operation = null;
        if("mysql".equals(conn.getDbServerType())) {
            operation = new DBOperationMysql(conn);
        } else if("mssql".equals(conn.getDbServerType())) {
            operation = new DBOperationMssql(conn);
        }
        return operation;
    }
}
