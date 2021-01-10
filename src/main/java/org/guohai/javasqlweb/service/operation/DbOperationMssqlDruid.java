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
import java.util.*;

import static org.guohai.javasqlweb.util.Utils.closeResource;

/**
 * 基于alibaba druid连接池的mysql实现类
 * @author guohai
 * @date 2021-1-5
 */
public class DbOperationMssqlDruid implements DbOperation {

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(DbOperationMssqlDruid.class);

    /**
     * 数据源
     */
    private DataSource sqlDs;


    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationMssqlDruid(ConnectConfigBean conn) throws Exception {

        Map dbConfig = new HashMap();
        dbConfig.put("url",String.format("jdbc:sqlserver://%s:%s",conn.getDbServerHost(),conn.getDbServerPort()));
        dbConfig.put("username",conn.getDbServerUsername());
        dbConfig.put("password",conn.getDbServerPassword());
        dbConfig.put("initialSize","2");
        dbConfig.put("validationQuery","select getdate()");
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
        ResultSet rs = st.executeQuery("SELECT database_id,name FROM sys.databases ;");
        while (rs.next()){
            listDnb.add(new DatabaseNameBean(rs.getObject("name").toString()));
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
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT a.name, b.rows FROM sysobjects a JOIN sysindexes b ON a.id = b.id " +
                "WHERE xtype = 'u' and indid in (0,1) ORDER BY a.name;", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getObject("name").toString(),
                    rs.getInt("rows")));
        }
        closeResource(rs,st,conn);
        return listTnb;
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
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT b.name column_name,c.name column_type,c.length column_length \n" +
                "FROM sysobjects a join syscolumns b on a.id=b.id and a.xtype='U'\n" +
                "join systypes c on b.xtype=c.xusertype\n" +
                "where a.name='%s'", dbName, tableName));
        while (rs.next()){
            listCnb.add(new ColumnsNameBean(rs.getObject("column_name").toString(),
                    rs.getObject("column_type").toString(),
                    rs.getObject("column_length").toString()));
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
        List<TableIndexesBean> listTib = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "exec sp_helpindex '%s'", dbName, tableName));
        while (rs.next()){
            listTib.add(new TableIndexesBean(rs.getObject("index_name").toString(),
                    rs.getObject("index_description").toString(),
                    rs.getObject("index_keys").toString()));
        }
        closeResource(rs,st,conn);
        return listTib;
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
        List<StoredProceduresBean> listSp = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "SELECT name FROM sysobjects WHERE type='P'", dbName));
        while (rs.next()){
            listSp.add(new StoredProceduresBean(rs.getString("name")));
        }
        closeResource(rs,st,conn);
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
        StoredProceduresBean spBean = null;
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "select definition from sys.sql_modules WHERE object_id = object_id('%s')", dbName, spName));
        while (rs.next()){
            spBean = new StoredProceduresBean(spName, rs.getString("definition"));
        }
        closeResource(rs,st,conn);
        return spBean;
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
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = st.executeQuery(String.format("use [%s];" +
                "%s;", dbName, sql));
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
                rowData.put(md.getColumnName(i),md.getColumnType(i) == 93
                        ? (rs.getObject(i)==null?"NULL":rs.getDate(i) + " " + rs.getTime(i))
                        : rs.getObject(i));
            }
            listData.add(rowData);
        }

        result[1] = listData.size();
        result[2] = listData;
        closeResource(rs,st,conn);
        return result;
    }

}
