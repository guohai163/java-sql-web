package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.util.HikariDataSourceUtils;

import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.guohai.javasqlweb.util.Utils.closeResource;

public class DbOperationClickHouse implements DbOperation{

    private static final long CACHED_DATASOURCE_IDLE_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_CACHED_DATASOURCES = 16;

    /**
     * 数据源
     */
    private final DataSource sqlDs;

    private final ConnectConfigBean connect;

    private final Map<String, CachedDataSource> queryDataSourceMap = new HashMap<>();

    private final QueryDataSourceFactory queryDataSourceFactory;

    private static final class CachedDataSource {
        private final DataSource dataSource;
        private long lastAccessAt;
        private int borrowCount;

        private CachedDataSource(DataSource dataSource, long lastAccessAt) {
            this.dataSource = dataSource;
            this.lastAccessAt = lastAccessAt;
        }
    }

    @FunctionalInterface
    interface QueryDataSourceFactory {
        DataSource create(String dbName) throws Exception;
    }

    /**
     * 构造方法
     * @param conn
     * @throws Exception
     */
    DbOperationClickHouse(ConnectConfigBean conn) throws Exception {
        this(createServerDataSource(conn), conn, null);
    }

    DbOperationClickHouse(DataSource dataSource) {
        this(dataSource, null, null);
    }

    DbOperationClickHouse(DataSource dataSource,
                          ConnectConfigBean conn,
                          QueryDataSourceFactory queryDataSourceFactory) {
        this.sqlDs = dataSource;
        this.connect = conn;
        this.queryDataSourceFactory = queryDataSourceFactory == null ? this::createQueryDataSource : queryDataSourceFactory;
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
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = sqlDs.getConnection();
            st = conn.createStatement();
            rs = st.executeQuery(String.format(
                    "SELECT name AS column_name, type AS column_type, comment AS column_comment " +
                            "FROM system.columns WHERE database='%s' AND table='%s' LIMIT 100;",
                    dbName,
                    tableName));
            while (rs.next()){
                String columnType = rs.getString("column_type");
                String columnComment = rs.getString("column_comment");
                listCnb.add(new ColumnsNameBean(rs.getString("column_name"),
                        columnType,
                        "",
                        columnComment == null ? "" : columnComment,
                        isNullableClickHouseType(columnType) ? "null" : "not null"));
            }
        } finally {
            closeResource(rs,st,conn);
        }
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
        DataSource queryDataSource = null;
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        int safeLimit = limit == null ? Integer.MAX_VALUE : Math.max(limit, 0);
        try{
            queryDataSource = acquireQueryDataSource(dbName);
            conn = queryDataSource.getConnection();
            st = conn.createStatement();
            //按【;】拆分SQL执行，默认最后一条为查询语句，为了方便使用SET @变量 = XXX
            sql = sql.replace("\n"," ");
            sql = sql.replace("\r"," ");

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
            releaseQueryDataSource(dbName, queryDataSource);
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
        synchronized (queryDataSourceMap) {
            for (CachedDataSource cachedDataSource : queryDataSourceMap.values()) {
                HikariDataSourceUtils.closeDataSource(cachedDataSource.dataSource);
            }
            queryDataSourceMap.clear();
        }
    }

    @Override
    public PoolStatBean describeRuntimePool() {
        synchronized (queryDataSourceMap) {
            cleanupQueryDataSources(System.currentTimeMillis(), null);
            if (queryDataSourceMap.isEmpty()) {
                return null;
            }
            PoolStatBean bean = new PoolStatBean();
            int poolCount = 0;
            String firstPoolName = null;
            for (CachedDataSource cachedDataSource : queryDataSourceMap.values()) {
                PoolStatBean current = describeDataSourcePool(cachedDataSource.dataSource);
                if (current == null) {
                    continue;
                }
                poolCount++;
                if (firstPoolName == null && current.getPoolName() != null && !current.getPoolName().isBlank()) {
                    firstPoolName = current.getPoolName();
                }
                if (bean.getJdbcUrl() == null) {
                    bean.setJdbcUrl(current.getJdbcUrl());
                }
                if (bean.getDriverClassName() == null) {
                    bean.setDriverClassName(current.getDriverClassName());
                }
                bean.setActiveConnections(defaultInteger(bean.getActiveConnections()) + defaultInteger(current.getActiveConnections()));
                bean.setIdleConnections(defaultInteger(bean.getIdleConnections()) + defaultInteger(current.getIdleConnections()));
                bean.setTotalConnections(defaultInteger(bean.getTotalConnections()) + defaultInteger(current.getTotalConnections()));
                bean.setThreadsAwaitingConnection(defaultInteger(bean.getThreadsAwaitingConnection()) + defaultInteger(current.getThreadsAwaitingConnection()));
            }
            if (poolCount == 0) {
                return null;
            }
            if (poolCount == 1) {
                bean.setPoolName(firstPoolName);
            } else if (firstPoolName != null && !firstPoolName.isBlank()) {
                bean.setPoolName(firstPoolName + " +" + (poolCount - 1));
            } else {
                bean.setPoolName("jsw-clickhouse-pools(" + poolCount + ")");
            }
            return bean;
        }
    }

    private boolean isNullableClickHouseType(String columnType) {
        return columnType != null && columnType.contains("Nullable(");
    }

    private static DataSource createServerDataSource(ConnectConfigBean conn) {
        return HikariDataSourceUtils.createDataSource(
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

    private DataSource createQueryDataSource(String dbName) {
        if (connect == null) {
            throw new IllegalStateException("ClickHouse query datasource factory requires connect config");
        }
        String normalizedDbName = normalizeDatabaseName(dbName);
        return HikariDataSourceUtils.createDataSource(
                String.format("jsw-clickhouse-%s-%s", connect.getCode(), Integer.toHexString(normalizedDbName.hashCode())),
                String.format(
                        "jdbc:clickhouse://%s:%s/%s?retry=0&client_retry_on_failures=None",
                        connect.getDbServerHost(),
                        connect.getDbServerPort(),
                        encodeDatabaseName(normalizedDbName)
                ),
                connect.getDbServerUsername(),
                connect.getDbServerPassword(),
                "select now()"
        );
    }

    private DataSource acquireQueryDataSource(String dbName) throws SQLException {
        String normalizedDbName = normalizeDatabaseName(dbName);
        if (normalizedDbName.isEmpty()) {
            return sqlDs;
        }
        synchronized (queryDataSourceMap) {
            long now = System.currentTimeMillis();
            cleanupQueryDataSources(now, normalizedDbName);
            CachedDataSource cachedDataSource = queryDataSourceMap.get(normalizedDbName);
            if (cachedDataSource == null) {
                cachedDataSource = new CachedDataSource(createQueryDataSourceSafely(normalizedDbName), now);
                queryDataSourceMap.put(normalizedDbName, cachedDataSource);
            }
            cachedDataSource.lastAccessAt = now;
            cachedDataSource.borrowCount++;
            return cachedDataSource.dataSource;
        }
    }

    private void releaseQueryDataSource(String dbName, DataSource dataSource) {
        String normalizedDbName = normalizeDatabaseName(dbName);
        if (dataSource == null || normalizedDbName.isEmpty() || dataSource == sqlDs) {
            return;
        }
        synchronized (queryDataSourceMap) {
            CachedDataSource cachedDataSource = queryDataSourceMap.get(normalizedDbName);
            if (cachedDataSource == null) {
                return;
            }
            if (cachedDataSource.borrowCount > 0) {
                cachedDataSource.borrowCount--;
            }
            cachedDataSource.lastAccessAt = System.currentTimeMillis();
            cleanupQueryDataSources(cachedDataSource.lastAccessAt, normalizedDbName);
        }
    }

    private void cleanupQueryDataSources(long now, String keepDbName) {
        List<String> idleKeys = new ArrayList<>();
        for (Map.Entry<String, CachedDataSource> entry : queryDataSourceMap.entrySet()) {
            if (keepDbName != null && keepDbName.equals(entry.getKey())) {
                continue;
            }
            CachedDataSource cachedDataSource = entry.getValue();
            if (cachedDataSource.borrowCount == 0
                    && now - cachedDataSource.lastAccessAt >= CACHED_DATASOURCE_IDLE_MILLIS) {
                idleKeys.add(entry.getKey());
            }
        }
        for (String dbName : idleKeys) {
            closeCachedQueryDataSource(dbName);
        }

        while (queryDataSourceMap.size() > MAX_CACHED_DATASOURCES) {
            String oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (Map.Entry<String, CachedDataSource> entry : queryDataSourceMap.entrySet()) {
                if (keepDbName != null && keepDbName.equals(entry.getKey())) {
                    continue;
                }
                CachedDataSource cachedDataSource = entry.getValue();
                if (cachedDataSource.borrowCount > 0) {
                    continue;
                }
                if (cachedDataSource.lastAccessAt < oldestAccess) {
                    oldestAccess = cachedDataSource.lastAccessAt;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                break;
            }
            closeCachedQueryDataSource(oldestKey);
        }
    }

    private void closeCachedQueryDataSource(String dbName) {
        CachedDataSource cachedDataSource = queryDataSourceMap.remove(dbName);
        if (cachedDataSource != null) {
            HikariDataSourceUtils.closeDataSource(cachedDataSource.dataSource);
        }
    }

    private PoolStatBean describeDataSourcePool(DataSource dataSource) {
        HikariDataSource hikariDataSource = unwrapHikariDataSource(dataSource);
        if (hikariDataSource == null) {
            return null;
        }
        PoolStatBean bean = new PoolStatBean();
        bean.setPoolName(hikariDataSource.getPoolName());
        bean.setJdbcUrl(hikariDataSource.getJdbcUrl());
        bean.setDriverClassName(hikariDataSource.getDriverClassName());
        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        if (poolMxBean != null) {
            bean.setActiveConnections(poolMxBean.getActiveConnections());
            bean.setIdleConnections(poolMxBean.getIdleConnections());
            bean.setTotalConnections(poolMxBean.getTotalConnections());
            bean.setThreadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection());
        }
        return bean;
    }

    private DataSource createQueryDataSourceSafely(String dbName) throws SQLException {
        try {
            return queryDataSourceFactory.create(dbName);
        } catch (SQLException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SQLException("create clickhouse datasource failed", exception);
        }
    }

    private String normalizeDatabaseName(String dbName) {
        return dbName == null ? "" : dbName.trim();
    }

    private String encodeDatabaseName(String dbName) {
        return URLEncoder.encode(dbName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HikariDataSource unwrapHikariDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource;
        }
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
