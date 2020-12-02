package org.guohai.javasqladmin.service.operation;

import org.guohai.javasqladmin.beans.*;

import java.sql.*;
import java.util.*;

/**
 * 基于微软MSSQL的操作类
 * @author guohai
 * @date 2020-12-1
 */
public class DBOperationMssql implements DBOperation {

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
     * 静态变量
     */
    private static final String DB_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private static final Integer LIMIT_NUMBER = 100;
    //endregion

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
        ResultSet rs = st.executeQuery(String.format("use %s;" +
                "SELECT a.name, b.rows FROM sysobjects a JOIN sysindexes b ON a.id = b.id " +
                "WHERE xtype = 'u' and indid in (0,1);", dbName));
        while (rs.next()){
            listTNB.add(new TablesNameBean(rs.getObject("name").toString(),
                                rs.getInt("rows")));
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

    /**
     * 获取所有的列
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        List<ColumnsNameBean> listCNB = new ArrayList<>();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use %s;" +
                "SELECT b.name column_name,c.name column_type,c.length column_length \n" +
                "FROM sysobjects a join syscolumns b on a.id=b.id and a.xtype='U'\n" +
                "join systypes c on b.xtype=c.xusertype\n" +
                "where a.name='%s'", dbName, tableName));
        while (rs.next()){
            listCNB.add(new ColumnsNameBean(rs.getObject("column_name").toString(),
                                            rs.getObject("column_type").toString(),
                                            rs.getObject("column_length").toString()));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listCNB;
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
        List<TableIndexesBean> listTIB = new ArrayList<>();
        Statement st = sqlConn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use %s;" +
                "exec sp_helpindex '%s'", dbName, tableName));
        while (rs.next()){
            listTIB.add(new TableIndexesBean(rs.getObject("index_name").toString(),
                    rs.getObject("index_description").toString(),
                    rs.getObject("index_keys").toString()));
        }
        // 关闭rs和statement
        if (rs != null) {
            rs.close();
        }
        if (st != null) {
            st.close();
        }
        return listTIB;
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
        ResultSet rs = st.executeQuery(String.format("use %s;" +
                "%s;", dbName, sql));
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
     * 检查SQL语句中是否有top属性
     * @return
     */
    private String limitSql(String sql){
        if (!sql.contains("top")) {
            return sql.replace("select", "select top " + LIMIT_NUMBER);
        }
        //TODO:包含top的也要做数量检查
        return sql;
    }
}
