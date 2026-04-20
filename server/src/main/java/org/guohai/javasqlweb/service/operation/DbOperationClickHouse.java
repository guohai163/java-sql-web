package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.guohai.javasqlweb.util.Utils.closeResource;

public class DbOperationClickHouse implements DbOperation{

    /**
     * 数据源
     */
    private DataSource sqlDs;

    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationClickHouse(ConnectConfigBean conn) throws Exception {
        sqlDs = HikariDataSourceUtils.createDataSource(
                "jsw-clickhouse-" + conn.getCode(),
                String.format(
                        "jdbc:clickhouse://%s:%s?retry=0&client_retry_on_failures=None",
                        conn.getDbServerHost(),
                        conn.getDbServerPort()
                ),
                conn.getDbServerUsername(),
                conn.getDbServerPassword(),
                "select now()"
        );
    }

    DbOperationClickHouse(DataSource dataSource) {
        this.sqlDs = dataSource;
    }


    /**
     * 获得实例服务器库列表
     *
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    @Override
    public List<DatabaseNameBean> getDbList() throws SQLException, ClassNotFoundException {
        List<DatabaseNameBean> listDnb = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SHOW DATABASES;");
        while (rs.next()){
            listDnb.add(new DatabaseNameBean(rs.getString("name")));
        }
        closeResource(rs,st,conn);
        return listDnb;
    }

    /**
     * 获得实例指定库的所有表名
     *
     * @param dbName 库名
     * @return 返回集合
     * @throws SQLException 抛出异常
     */
    @Override
    public List<TablesNameBean> getTableList(String dbName) throws SQLException {
        List<TablesNameBean> listTnb = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SELECT name,total_rows FROM system.tables where database='%s' ORDER BY name DESC; ", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getString("name"),
                    rs.getLong("total_rows")));
        }
        closeResource(rs,st,conn);
        return listTnb;
    }

    /**
     * 取回视图列表
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public List<ViewNameBean> getViewsList(String dbName) throws SQLException {
        return null;
    }

    /**
     * 获取视图详细信息
     *
     * @param dbName
     * @param viewName
     * @return
     * @throws SQLException
     */
    @Override
    public ViewNameBean getView(String dbName, String viewName) throws SQLException {
        return null;
    }

    /**
     * 获取所有列名
     *
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public List<ColumnsNameBean> getColumnsList(String dbName, String tableName) throws SQLException {
        List<ColumnsNameBean> listCnb = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("SELECT name,type,comment FROM system.columns where database='%s' and table='%s' limit 100;", dbName, tableName));
        while (rs.next()){
            listCnb.add(new ColumnsNameBean(rs.getString("Field"),
                    rs.getString("Type"),
                    "",
                    rs.getString("Comment"),
                    "NO".equals(rs.getString("Null"))?"not null":"null"));
        }
        closeResource(rs,st,conn);
        return listCnb;
    }

    /**
     * 获取所有的索引数据
     *
     * @param dbName
     * @param tableName
     * @return
     * @throws SQLException 抛出异常
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
        return null;
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
     * @param limit
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public Object[] queryDatabaseBySql(String dbName, String sql, Integer limit) throws SQLException {
        Object[] result = new Object[3];
        List<Map<String, Object>> listData = new ArrayList<>();
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        int safeLimit = limit == null ? Integer.MAX_VALUE : Math.max(limit, 0);
        try{
            conn = sqlDs.getConnection();
            st = conn.createStatement();
            //按【;】拆分SQL执行，默认最后一条为查询语句，为了方便使用SET @变量 = XXX
            sql = sql.replace("\n"," ");
            sql = sql.replace("\r"," ");

            st.execute(String.format("use %s", dbName));
            rs = st.executeQuery(String.format("%s;", sql));
            // 获得结果集结构信息,元数据
            java.sql.ResultSetMetaData md = rs.getMetaData();
            // 获得列数
            int columnCount = md.getColumnCount();
            boolean hasMore = false;
            while (rs.next()){
                if(listData.size() >= safeLimit){
                    hasMore = true;
                    break;
                }
                Map<String, Object> rowData = new LinkedHashMap<>();
                for(int i=1;i<=columnCount;i++){
                    Object object = rs.getObject(i);
                    //时间类型特殊处理
                    if (md.getColumnType(i) == Types.TIMESTAMP) {
                        object = object == null ? "NULL" : String.valueOf(rs.getTimestamp(i));
                    }
                    rowData.put(md.getColumnLabel(i), object);
                }
                listData.add(rowData);
            }

            result[0] = hasMore ? listData.size() + 1 : listData.size();
            result[1] = listData.size();
            result[2] = listData;
        } finally {
            closeResource(rs,st,conn);
        }


        return result;
    }

    /**
     * 返回一个数据库的所有表和列集合
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public Map<String, String[]> getTablesColumnsMap(String dbName) throws SQLException {
        return null;
    }

    /**
     * 服务器连接状态健康检查
     *
     * @return
     * @throws SQLException
     */
    @Override
    public Boolean serverHealth() throws SQLException {
        return null;
    }

    @Override
    public void close() {
        HikariDataSourceUtils.closeDataSource(sqlDs);
    }
}
