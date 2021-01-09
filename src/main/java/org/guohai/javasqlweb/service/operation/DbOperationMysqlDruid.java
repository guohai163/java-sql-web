package org.guohai.javasqlweb.service.operation;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import org.guohai.javasqlweb.beans.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbOperationMysqlDruid implements DBOperation{

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(DbOperationMysqlDruid.class);

    /**
     * 数据源
     */
    private DataSource sqlDs;

    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationMysqlDruid(ConnectConfigBean conn) throws Exception {

        Map dbConfig = new HashMap();
        dbConfig.put("url",String.format("jdbc:mysql://%s:%s?useUnicode=true&characterEncoding=UTF-8",
                conn.getDbServerHost(),conn.getDbServerPort()));
        dbConfig.put("username",conn.getDbServerUsername());
        dbConfig.put("password",conn.getDbServerPassword());
        dbConfig.put("initialSize","2");
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
        return null;
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
     * @return
     * @throws SQLException 抛出异常
     */
    @Override
    public Object[] queryDatabaseBySql(String dbName, String sql) throws SQLException {
        return null;
    }
}
