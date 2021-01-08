package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于微软MSSQL的操作类
 * @author guohai
 * @date 2020-12-1
 */
public class DBOperationMysql implements DBOperation {

    //region 私有变量区
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
     * 最大查询限制
     */
    private static final Integer LIMIT_NUMBER = 100;
    /**
     * 数据驱动
     */
    private static final String DB_DRIVER = "com.mysql.cj.jdbc.Driver";

    //endregion

    /**
     * 构造方法
     * @param conn
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    DBOperationMysql(ConnectConfigBean conn) throws ClassNotFoundException, SQLException {
        connectConfigBean = conn;
        sqlUrl = String.format("jdbc:mysql://%s:%s?useUnicode=true&characterEncoding=UTF-8",conn.getDbServerHost(),conn.getDbServerPort());
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
    public List<DatabaseNameBean> getDbList() throws SQLException {
        List<DatabaseNameBean> listDnb = new ArrayList<>();
        // jiancha lianjie
        checkConnection();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery("SHOW DATABASES;");
        while (rs.next()){
            listDnb.add(new DatabaseNameBean(rs.getString("Database")));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listDnb;
    }

    /**
     * 获得实例指定库的所有表名
     *
     * @param dbName
     * @return
     */
    @Override
    public List<TablesNameBean> getTableList(String dbName) throws SQLException {
        List<TablesNameBean> listTnb = new ArrayList<>();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SELECT table_name ,table_rows\n" +
                        "FROM `information_schema`.`tables` WHERE TABLE_SCHEMA = '%s' ORDER BY table_rows DESC;", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getObject("table_name").toString(),
                    rs.getInt("table_rows")));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listTnb;
    }

    /**
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        List<ColumnsNameBean> listCnb = new ArrayList<>();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SHOW FULL COLUMNS FROM %s.%s", dbName, tableName));
        while (rs.next()){
            listCnb.add(new ColumnsNameBean(rs.getObject("Field").toString(),
                    rs.getObject("Type").toString(),
                    rs.getObject("Type").toString()));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listCnb;
    }

    /**
     * 获取所有的索引数据
     *
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public List<TableIndexesBean> getIndexesList(String dbName, String tableName) throws SQLException {
        return null;
    }

    /**
     * 获取指定库的所有存储过程列表
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public List<StoredProceduresBean> getStoredProceduresList(String dbName) throws SQLException {
        //SELECT SPECIFIC_NAME FROM information_schema.Routines WHERE ROUTINE_SCHEMA='javasqlweb_db'
        List<StoredProceduresBean> listSp = new ArrayList<>();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SELECT SPECIFIC_NAME FROM information_schema.Routines WHERE ROUTINE_SCHEMA='%s'", dbName));
        while (rs.next()){
            listSp.add(new StoredProceduresBean(rs.getString("name")));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listSp;
    }

    /**
     * 获取指定存储过程内容
     *
     * @param dbName
     * @param spName
     * @return
     * @throws SQLException
     */
    @Override
    public StoredProceduresBean getStoredProcedure(String dbName, String spName) throws SQLException {
        return null;
    }

    /**
     * 执行查询的SQL
     *
     * @param dbName
     * @param sql
     * @return
     */
    @Override
    public Object queryDatabaseBySql(String dbName, String sql) throws SQLException {
        List<Map<String, Object>> listData = new ArrayList<>();
        // TODO: 缺少SQL检查
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format("%s;", sql));
        // 获得结果集结构信息,元数据
        java.sql.ResultSetMetaData md = rs.getMetaData();
        // 获得列数
        int columnCount = md.getColumnCount();
        while (rs.next()){
            Map<String, Object> rowData = new LinkedHashMap<String, Object>();
            for(int i=1;i<=columnCount;i++){
                rowData.put(md.getColumnName(i),md.getColumnType(i) == 93
                        ? rs.getDate(i) + " " + rs.getTime(i)
                        : rs.getObject(i));
            }
            listData.add(rowData);
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listData;
    }


    /**
     * 检查连接状态后再使用
     */
    private void checkConnection(){
        try {
            if(sqlConn.isClosed()){
                sqlConn = DriverManager.getConnection(sqlUrl,
                        connectConfigBean.getDbServerUsername(),
                        connectConfigBean.getDbServerPassword());
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
}
