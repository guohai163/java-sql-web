package org.guohai.javasqlweb.service.operation;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import org.guohai.javasqlweb.beans.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationPostgresqlDruid(ConnectConfigBean conn) throws Exception {

        Map dbConfig = new HashMap();
        dbConfig.put("url",String.format("jdbc:postgresql://%s:%s/postgres",
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
        ResultSet rs = st.executeQuery("SELECT datname FROM pg_database;");
        while (rs.next()){
            listDnb.add(new DatabaseNameBean(rs.getString("datname")));
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
        return new Object[0];
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
}
