package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;
import org.guohai.javasqladmin.beans.TablesNameBean;

import java.sql.*;
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
     * 连接实例
     */
    private Connection sqlConn;

    /**
     * 静态变量
     */
    private static final String DB_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    /**
     * 构造方法
     * @param conn
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    DBOperationMssql(ConnectConfigBean conn) throws ClassNotFoundException, SQLException {
        connectConfigBean = conn;
        sqlUrl = String.format("jdbc:sqlserver://%s:%s",conn.getDbServerHost(),conn.getDbServerPort());
        Class.forName(DB_DRIVER);
        sqlConn = DriverManager.getConnection(sqlUrl,
                connectConfigBean.getDbServerUsername(),
                connectConfigBean.getDbServerPassword());
    }

    /**
     * 获得实例服务器库列表
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    @Override
    public List<DatabaseNameBean> getDBList() throws SQLException {
        List<DatabaseNameBean> listDNB = new ArrayList<>();
        Statement st = sqlConn.createStatement();
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

    /**
     * 获得实例指定库的所有表名
     *
     * @param dbName
     * @return
     */
    @Override
    public List<TablesNameBean> getTableList(String dbName) throws SQLException {
        List<TablesNameBean> listTNB = new ArrayList<>();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format("SELECT name FROM %s..sysobjects WHERE xtype = 'u';", dbName));
        while (rs.next()){
            listTNB.add(new TablesNameBean(rs.getObject("name").toString()));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listTNB;
    }
}
