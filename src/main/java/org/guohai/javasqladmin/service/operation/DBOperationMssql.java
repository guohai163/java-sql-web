package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于微软MSSQL的操作类
 * @author guohai
 * @date 2020-12-1
 */
public class DBOperationMssql implements DBOperation {

    /**
     * 数据库配置
     */
    private ConnectConfigBean connectConfigBean;
    /**
     * 连接字符串
     */
    private String sqlUrl;

    /**
     * 静态变量
     */
    private static final String DB_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    DBOperationMssql(ConnectConfigBean conn){
        connectConfigBean = conn;
        sqlUrl = String.format("jdbc:sqlserver://%s:%s",conn.getDbServerHost(),conn.getDbServerPort());
    }

    @Override
    public List<DatabaseNameBean> getDBList() throws SQLException, ClassNotFoundException {
        Class.forName(DB_DRIVER);
        List<DatabaseNameBean> listDNB = new ArrayList<>();
        java.sql.Connection sqlServerConn = DriverManager.getConnection(sqlUrl,
                connectConfigBean.getDbServerUsername(),
                connectConfigBean.getDbServerPassword());
        Statement st = sqlServerConn.createStatement();
        ResultSet rs = st.executeQuery("SELECT database_id,name FROM sys.databases ;");
        while (rs.next()){
            listDNB.add(new DatabaseNameBean(rs.getObject("name").toString()));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listDNB;
    }
}
