package org.guohai.javasqlweb.service.operation;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import org.guohai.javasqlweb.beans.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

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

        Map dbConfig = new HashMap();
        dbConfig.put("url",String.format("jdbc:clickhouse://%s:%s",
                conn.getDbServerHost(),conn.getDbServerPort()));
        dbConfig.put("username",conn.getDbServerUsername());
        dbConfig.put("password",conn.getDbServerPassword());
        dbConfig.put("initialSize","2");
        dbConfig.put("minIdle","1");
        dbConfig.put("maxWait","10000");
        dbConfig.put("maxActive","20");
        dbConfig.put("validationQuery","select now()");
        sqlDs = DruidDataSourceFactory.createDataSource(dbConfig);
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
        try{
            conn = sqlDs.getConnection();
            st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
            //选择一个数据库
//            st.execute("use ".concat(dbName));
            //为了判断是否超过返回条数限制
            st.setMaxRows(limit + 1);
            //按【;】拆分SQL执行，默认最后一条为查询语句，为了方便使用SET @变量 = XXX
            sql = sql.replace("\n"," ");
            sql = sql.replace("\r"," ");

            //执行sql
            rs = st.executeQuery(String.format("use %s;" +
                    "%s;", dbName, sql));
            // 获得结果集结构信息,元数据
            java.sql.ResultSetMetaData md = rs.getMetaData();
            // 获得列数
            int columnCount = md.getColumnCount();
            int dataCount = 1;
            result[0] = 0;
            while (rs.next()){
                if(dataCount>limit){
                    result[0] = dataCount;
                    break;
                }
                dataCount++;
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
}
