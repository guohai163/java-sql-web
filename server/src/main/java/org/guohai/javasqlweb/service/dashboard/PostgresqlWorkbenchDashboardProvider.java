package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.util.DbServerTypeUtils;

import java.util.List;

public class PostgresqlWorkbenchDashboardProvider extends AbstractWorkbenchDashboardProvider {

    @Override
    public String getDbType() {
        return DbServerTypeUtils.POSTGRESQL;
    }

    @Override
    public List<WorkbenchDashboardSection> buildSections(DbOperation operation, String dbName, ConnectConfigBean config) {
        return List.of(
                section("system", "系统信息",
                        staticItem("server_name", "实例名称", config.getDbServerName()),
                        staticItem("server_host", "服务器地址", config.getDbServerHost()),
                        queryItem(operation, dbName, "version", "版本", "SELECT version() AS value", "value"),
                        queryItem(operation, dbName, "start_time", "启动时间", "SELECT pg_postmaster_start_time() AS value", "value"),
                        queryItem(operation, dbName, "connection_count", "当前连接数", "SELECT COUNT(*) AS value FROM pg_stat_activity", "value"),
                        queryItem(operation, dbName, "active_sessions", "活跃会话数", "SELECT COUNT(*) AS value FROM pg_stat_activity WHERE state = 'active'", "value"),
                        queryItem(operation, dbName, "waiting_sessions", "等待会话数",
                                "SELECT COUNT(*) AS value FROM pg_stat_activity WHERE wait_event IS NOT NULL", "value")
                ),
                section("database", "数据库信息",
                        staticItem("database_name", "当前数据库", dbName),
                        queryItem(operation, dbName, "database_size", "库大小", "SELECT pg_size_pretty(pg_database_size(current_database())) AS value", "value"),
                        queryItem(operation, dbName, "table_count", "public 表数量",
                                "SELECT COUNT(*) AS value FROM information_schema.tables WHERE table_schema = 'public'", "value"),
                        queryItem(operation, dbName, "xact_commit", "提交事务数",
                                "SELECT xact_commit AS value FROM pg_stat_database WHERE datname = current_database()", "value"),
                        queryItem(operation, dbName, "deadlocks", "死锁数",
                                "SELECT deadlocks AS value FROM pg_stat_database WHERE datname = current_database()", "value"),
                        queryItem(operation, dbName, "cache_hit_rate", "缓存命中率(%)",
                                "SELECT ROUND(CASE WHEN blks_hit + blks_read = 0 THEN 0 ELSE blks_hit * 100.0 / (blks_hit + blks_read) END, 2) AS value " +
                                        "FROM pg_stat_database WHERE datname = current_database()", "value")
                ),
                section("extra", "其他信息",
                        queryItem(operation, dbName, "temp_bytes", "临时文件占用",
                                "SELECT pg_size_pretty(temp_bytes) AS value FROM pg_stat_database WHERE datname = current_database()", "value"),
                        queryItem(operation, dbName, "schemas", "schema 数量",
                                "SELECT COUNT(*) AS value FROM information_schema.schemata", "value"),
                        queryItem(operation, dbName, "indexes", "public 索引数量",
                                "SELECT COUNT(*) AS value FROM pg_indexes WHERE schemaname = 'public'", "value"),
                        queryItem(operation, dbName, "long_running_queries", "长查询数(>60s)",
                                "SELECT COUNT(*) AS value FROM pg_stat_activity WHERE state = 'active' AND now() - query_start > interval '60 second'", "value")
                )
        );
    }
}
