package org.guohai.javasqlweb.service.operation;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import org.guohai.javasqlweb.beans.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static org.guohai.javasqlweb.util.Utils.closeResource;

/**
 * Mysql操作实现类
 * @author guohai
 * @date 2021-1-1
 */
public class DbOperationMysqlDruid implements DbOperation {

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
        dbConfig.put("url",String.format("jdbc:mysql://%s:%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true",
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
            listDnb.add(new DatabaseNameBean(rs.getString("Database")));
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
                "SELECT table_name ,table_rows " +
                "FROM `information_schema`.`tables` WHERE TABLE_SCHEMA = '%s' ORDER BY table_name DESC;", dbName));
        while (rs.next()){
            listTnb.add(new TablesNameBean(rs.getString("table_name"),
                    rs.getInt("table_rows")));
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
        List<ViewNameBean> listView = new ArrayList<>();
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format(
                "SELECT TABLE_NAME,VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA='%s'", dbName));
        while (rs.next()){
            listView.add(new ViewNameBean(rs.getString("TABLE_NAME"), rs.getString("VIEW_DEFINITION")));
        }
        closeResource(rs,st,conn);
        return listView;
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
        ResultSet rs = st.executeQuery(String.format("SHOW FULL COLUMNS FROM %s.%s", dbName, tableName));
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
     * 返回一个数据库的所有表和列集合
     *
     * @param dbName
     * @return
     * @throws SQLException
     */
    @Override
    public Map<String, String[]> getTablesColumnsMap(String dbName) throws SQLException {
        Map<String, String[]> tables = new HashMap<>(10);
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(String.format("select TABLE_NAME,group_concat(COLUMN_NAME) as COLUMN_NAME from `information_schema`.`COLUMNS` where  TABLE_SCHEMA='%s' group by TABLE_NAME;", dbName));
        while (rs.next()){
            tables.put(rs.getString("TABLE_NAME"),rs.getString("COLUMN_NAME").split(","));
        }
        closeResource(rs,st,conn);
        return tables;
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
     * @param dbName 数据库db
     * @return 存储过程名
     * @throws SQLException
     */
    @Override
    public List<StoredProceduresBean> getStoredProceduresList(String dbName) throws SQLException {
        List<StoredProceduresBean> listSp = new ArrayList<>();
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            st = conn.createStatement();
            rs = st.executeQuery(String.format(
                    "SELECT SPECIFIC_NAME FROM information_schema.Routines WHERE ROUTINE_SCHEMA='%s'", dbName));
            while (rs.next()) {
                listSp.add(new StoredProceduresBean(rs.getString("SPECIFIC_NAME")));
            }
        } finally {
            closeResource(rs, st, conn);
        }
        return listSp;
    }

    /**
     * 获取指定存储过程内容
     *
     * @param dbName 数据库db
     * @param spName 存储过程名
     * @return StoredProceduresBean 存储过程内容
     * @throws SQLException
     */
    @Override
    public StoredProceduresBean getStoredProcedure(String dbName, String spName) throws SQLException {
        StoredProceduresBean spBean = null;
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            st = conn.createStatement();
            rs = st.executeQuery(String.format("SHOW CREATE PROCEDURE %s.%s;", dbName, spName));
            while (rs.next()) {
                spBean = new StoredProceduresBean(spName, rs.getString("Create Procedure"));
            }
        } finally {
            closeResource(rs, st, conn);
        }
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
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try{
            conn = sqlDs.getConnection();
            st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
            //选择一个数据库
            st.execute("use ".concat(dbName));
            st.setMaxRows(limit);
            //按【;】拆分SQL执行，默认最后一条为查询语句，为了方便使用SET @变量 = XXX
            sql = sql.replace("\n","");
            sql = sql.replace("\r","");
            String[] splitSql = sql.split(";");
            for (int i = 0; i < splitSql.length - 1; i++) {
                st.execute(String.format("%s;", splitSql[i]));
            }

            //执行sql
            rs = st.executeQuery(String.format("%s;", splitSql[splitSql.length - 1]));
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
     * 服务器连接状态健康检查
     *
     * @return
     * @throws SQLException
     */
    @Override
    public Boolean serverHealth() throws SQLException {
        Connection conn = sqlDs.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT now()");
        closeResource(rs,st,conn);
        return true;
    }


}
