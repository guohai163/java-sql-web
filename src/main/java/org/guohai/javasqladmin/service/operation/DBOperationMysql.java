package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;

import java.util.List;

public class DBOperationMysql implements DBOperation {

    private ConnectConfigBean connectConfigBean;
    DBOperationMysql(ConnectConfigBean conn){
        connectConfigBean = conn;
    }

    @Override
    public List<DatabaseNameBean> getDBList() {
        return null;
    }
}
