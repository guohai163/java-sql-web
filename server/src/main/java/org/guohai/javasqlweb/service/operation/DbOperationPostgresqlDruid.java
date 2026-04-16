package org.guohai.javasqlweb.service.operation;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.service.BaseDataServiceImpl;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static org.guohai.javasqlweb.util.Utils.closeResource;

public class DbOperationPostgresqlDruid implements DbOperation {
    private static final String PUBLIC_SCHEMA = "public";

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
        sqlDs = createDataSource("postgres");
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
                            postgresMap.put(dbName, createDataSource(dbName));
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
                    rs.getLong("rowCounts")));
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
        List<ViewNameBean> viewList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT viewname " +
                            "FROM pg_catalog.pg_views " +
                            "WHERE schemaname = ? " +
                            "ORDER BY viewname"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            rs = ps.executeQuery();
            while (rs.next()) {
                viewList.add(new ViewNameBean(rs.getString("viewname")));
            }
        } finally {
            closeResource(rs, ps, conn);
        }
        return viewList;
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
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT COALESCE(definition, '') AS view_definition " +
                            "FROM pg_catalog.pg_views " +
                            "WHERE schemaname = ? AND viewname = ?"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, viewName);
            rs = ps.executeQuery();
            if (rs.next()) {
                String definition = rs.getString("view_definition");
                return new ViewNameBean(
                        viewName,
                        String.format("CREATE OR REPLACE VIEW %s.%s AS%n%s",
                                quoteIdentifier(PUBLIC_SCHEMA),
                                quoteIdentifier(viewName),
                                definition == null ? "" : definition)
                );
            }
            return new ViewNameBean(viewName, "");
        } finally {
            closeResource(rs, ps, conn);
        }
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
        List<ColumnsNameBean> columnList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT c.column_name, " +
                            "       CASE " +
                            "           WHEN c.data_type = 'USER-DEFINED' THEN c.udt_name " +
                            "           WHEN c.data_type = 'ARRAY' THEN c.udt_name " +
                            "           ELSE c.data_type " +
                            "       END AS column_type, " +
                            "       CASE " +
                            "           WHEN c.character_maximum_length IS NOT NULL THEN c.character_maximum_length::text " +
                            "           WHEN c.numeric_precision IS NOT NULL AND c.numeric_scale IS NOT NULL THEN c.numeric_precision::text || ',' || c.numeric_scale::text " +
                            "           WHEN c.numeric_precision IS NOT NULL THEN c.numeric_precision::text " +
                            "           WHEN c.datetime_precision IS NOT NULL THEN c.datetime_precision::text " +
                            "           ELSE '' " +
                            "       END AS column_length, " +
                            "       COALESCE(pd.description, '') AS column_comment, " +
                            "       CASE WHEN c.is_nullable = 'NO' THEN 'not null' ELSE 'null' END AS column_is_null " +
                            "FROM information_schema.columns c " +
                            "LEFT JOIN pg_catalog.pg_namespace pn " +
                            "       ON pn.nspname = c.table_schema " +
                            "LEFT JOIN pg_catalog.pg_class pc " +
                            "       ON pc.relname = c.table_name AND pc.relnamespace = pn.oid " +
                            "LEFT JOIN pg_catalog.pg_attribute pa " +
                            "       ON pa.attrelid = pc.oid AND pa.attname = c.column_name " +
                            "LEFT JOIN pg_catalog.pg_description pd " +
                            "       ON pd.objoid = pc.oid AND pd.objsubid = pa.attnum " +
                            "WHERE c.table_schema = ? AND c.table_name = ? " +
                            "ORDER BY c.ordinal_position"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, tableName);
            rs = ps.executeQuery();
            while (rs.next()) {
                columnList.add(new ColumnsNameBean(
                        rs.getString("column_name"),
                        rs.getString("column_type"),
                        rs.getString("column_length"),
                        rs.getString("column_comment"),
                        rs.getString("column_is_null")
                ));
            }
        } finally {
            closeResource(rs, ps, conn);
        }
        return columnList;
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
        List<TableIndexesBean> indexList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT ci.relname AS index_name, " +
                            "       pg_get_indexdef(i.indexrelid) AS index_description, " +
                            "       COALESCE(string_agg(a.attname, ', ' ORDER BY k.ordinality), '') AS index_keys " +
                            "FROM pg_catalog.pg_class ct " +
                            "JOIN pg_catalog.pg_namespace nt " +
                            "  ON nt.oid = ct.relnamespace " +
                            "JOIN pg_catalog.pg_index i " +
                            "  ON i.indrelid = ct.oid " +
                            "JOIN pg_catalog.pg_class ci " +
                            "  ON ci.oid = i.indexrelid " +
                            "LEFT JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, ordinality) " +
                            "  ON TRUE " +
                            "LEFT JOIN pg_catalog.pg_attribute a " +
                            "  ON a.attrelid = ct.oid AND a.attnum = k.attnum " +
                            "WHERE nt.nspname = ? " +
                            "  AND ct.relname = ? " +
                            "  AND ct.relkind = 'r' " +
                            "GROUP BY ci.relname, i.indexrelid " +
                            "ORDER BY ci.relname"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, tableName);
            rs = ps.executeQuery();
            while (rs.next()) {
                indexList.add(new TableIndexesBean(
                        rs.getString("index_name"),
                        rs.getString("index_description"),
                        rs.getString("index_keys")
                ));
            }
        } finally {
            closeResource(rs, ps, conn);
        }
        return indexList;
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
        List<StoredProceduresBean> procedureList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT DISTINCT routine_name " +
                            "FROM information_schema.routines " +
                            "WHERE specific_schema = ? " +
                            "ORDER BY routine_name"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            rs = ps.executeQuery();
            while (rs.next()) {
                procedureList.add(new StoredProceduresBean(rs.getString("routine_name")));
            }
        } finally {
            closeResource(rs, ps, conn);
        }
        return procedureList;
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
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT COALESCE(pg_get_functiondef(p.oid), '') AS routine_definition " +
                            "FROM pg_catalog.pg_proc p " +
                            "JOIN pg_catalog.pg_namespace n " +
                            "  ON n.oid = p.pronamespace " +
                            "WHERE n.nspname = ? " +
                            "  AND p.proname = ? " +
                            "ORDER BY p.oid " +
                            "LIMIT 1"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, spName);
            rs = ps.executeQuery();
            if (rs.next()) {
                return new StoredProceduresBean(spName, rs.getString("routine_definition"));
            }
            return new StoredProceduresBean(spName, "");
        } finally {
            closeResource(rs, ps, conn);
        }
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
        Map<String, String[]> tables = new HashMap<>(16);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getRequiredDataSource(dbName).getConnection();
            ps = conn.prepareStatement(
                    "SELECT table_name, string_agg(column_name, ',' ORDER BY ordinal_position) AS column_names " +
                            "FROM information_schema.columns " +
                            "WHERE table_schema = ? " +
                            "GROUP BY table_name"
            );
            ps.setString(1, PUBLIC_SCHEMA);
            rs = ps.executeQuery();
            while (rs.next()) {
                String columnNames = rs.getString("column_names");
                tables.put(
                        rs.getString("table_name"),
                        columnNames == null || columnNames.isEmpty() ? new String[0] : columnNames.split(",")
                );
            }
        } finally {
            closeResource(rs, ps, conn);
        }
        return tables;
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

    @Override
    public void close() {
        HikariDataSourceUtils.closeDataSource(sqlDs);
        for (DataSource dataSource : postgresMap.values()) {
            HikariDataSourceUtils.closeDataSource(dataSource);
        }
        postgresMap.clear();
    }


    /**
     * 通过库名制造一个连接串
     * @param dbName
     * @return
     */
    private DataSource createDataSource(String dbName){
        return HikariDataSourceUtils.createDataSource(
                "jsw-postgresql-" + dbName,
                String.format("jdbc:postgresql://%s:%s/%s",
                        connect.getDbServerHost(), connect.getDbServerPort(), dbName),
                connect.getDbServerUsername(),
                connect.getDbServerPassword(),
                "select now()"
        );
    }

    private DataSource getRequiredDataSource(String dbName) {
        DataSource dataSource = postgresMap.get(dbName);
        if (dataSource != null) {
            return dataSource;
        }
        synchronized (BaseDataServiceImpl.class) {
            DataSource existing = postgresMap.get(dbName);
            if (existing != null) {
                return existing;
            }
            DataSource created = createDataSource(dbName);
            postgresMap.put(dbName, created);
            return created;
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

}
