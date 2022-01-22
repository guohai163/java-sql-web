package org.guohai.javasqlweb.service.operation;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.service.BaseDataServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.guohai.javasqlweb.util.Utils.closeResource;

public class DbOperationPostgresqlDruid implements DbOperation {

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(DbOperationPostgresqlDruid.class);

    /**
     * 数据源
     */
    private DataSource sqlDs;

    /**
     * 保存每个库的连接
     * 因为postgres数据库的特殊性无法在一个连接里访问多个库，所以 得制造多份连接。
     */
    private Map<String, DataSource> postgresMap = new HashMap<>();
    /**
     * 保存连接资源
     */
    private ConnectConfigBean connect;
    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationPostgresqlDruid(ConnectConfigBean conn) throws Exception {

        connect = conn;
        sqlDs = DruidDataSourceFactory.createDataSource(getDbConfigMap("postgres"));
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
        ResultSet rs = st.executeQuery("SELECT datname FROM pg_database;");
        while (rs.next()){

            String dbName = rs.getString("datname");
            if("template0".equals(dbName) || "template1".equals(dbName)){
                continue;
            }
            listDnb.add(new DatabaseNameBean(dbName));
            // 创建库的连接
            DataSource sqlDS = postgresMap.get(dbName);
            if(null == sqlDS) {
                synchronized (BaseDataServiceImpl.class) {
                    if (null == postgresMap.get(dbName)) {
                        try {
                            postgresMap.put(dbName,DruidDataSourceFactory.createDataSource(getDbConfigMap(dbName)));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
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
        Connection conn = postgresMap.get(dbName).getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "select relname as TABLE_NAME, reltuples as rowCounts from pg_class\n" +
                        "where relkind = 'r' and relnamespace = (select oid from pg_namespace where nspname='public')\n" +
                        "order by rowCounts desc;", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getString("TABLE_NAME"),
                    rs.getInt("rowCounts")));
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
        return null;
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
        Connection conn = postgresMap.get(dbName).getConnection();
        Statement st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);

        ResultSet rs = null;
        try{

            rs = st.executeQuery(String.format("%s;", sql));
            // 获得结果集结构信息,元数据
            java.sql.ResultSetMetaData md = rs.getMetaData();
            // 获得列数
            int columnCount = md.getColumnCount();
            rs.last();
            result[0] = rs.getRow();
            rs.beforeFirst();
            int dataCount = 1;
            while (rs.next()){
                if(dataCount>limit){
                    break;
                }
                dataCount++;
                Map<String, Object> rowData = new LinkedHashMap<String, Object>();
                for(int i=1;i<=columnCount;i++){
                    rowData.put(md.getColumnLabel(i),md.getColumnType(i) == 93
                            ? (rs.getObject(i)==null?"NULL":rs.getDate(i) + " " + rs.getTime(i))
                            : rs.getObject(i));
                }
                listData.add(rowData);
            }

            result[1] = listData.size();
            result[2] = listData;
        }
        catch (SQLException e){
            throw e;
        }
        finally {
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
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT now();");
        closeResource(rs,st,conn);
        return true;
    }


    /**
     * 通过库名制造一个连接串
     * @param dbName
     * @return
     */
    private Map getDbConfigMap(String dbName){
        Map dbConfig = new HashMap(8);
        dbConfig.put("url",String.format("jdbc:postgresql://%s:%s/%s",
                connect.getDbServerHost(),connect.getDbServerPort(),dbName));
        dbConfig.put("username",connect.getDbServerUsername());
        dbConfig.put("password",connect.getDbServerPassword());
        dbConfig.put("initialSize","2");
        dbConfig.put("minIdle","1");
        dbConfig.put("maxWait","10000");
        dbConfig.put("maxActive","20");
        dbConfig.put("validationQuery","select now()");
        return dbConfig;
    }

}
