package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.util.DbServerTypeUtils;

import java.util.List;

public class ClickHouseWorkbenchDashboardProvider extends AbstractWorkbenchDashboardProvider {

    @Override
    public String getDbType() {
        return DbServerTypeUtils.CLICKHOUSE;
    }

    @Override
    public List<WorkbenchDashboardSection> buildSections(DbOperation operation, String dbName, ConnectConfigBean config) {
        return List.of(
                section("system", "系统信息",
                        staticItem("server_name", "实例名称", config.getDbServerName()),
                        staticItem("server_host", "服务器地址", config.getDbServerHost()),
                        queryItem(operation, dbName, "version", "版本", "SELECT version() AS value", "value"),
                        queryItem(operation, dbName, "uptime", "运行时长(秒)", "SELECT uptime() AS value", "value"),
                        queryItem(operation, dbName, "current_queries", "当前查询数", "SELECT COUNT(*) AS value FROM system.processes", "value"),
                        queryItem(operation, dbName, "memory_tracking", "内存跟踪(bytes)",
                                "SELECT value AS value FROM system.metrics WHERE metric = 'MemoryTracking'", "value")
                ),
                section("database", "数据库信息",
                        staticItem("database_name", "当前数据库", dbName),
                        queryItem(operation, dbName, "table_count", "表数量",
                                "SELECT COUNT(*) AS value FROM system.tables WHERE database = currentDatabase()", "value"),
                        queryItem(operation, dbName, "total_rows", "总行数",
                                "SELECT COALESCE(sum(total_rows), 0) AS value FROM system.tables WHERE database = currentDatabase()", "value"),
                        queryItem(operation, dbName, "database_size", "库大小",
                                "SELECT formatReadableSize(COALESCE(sum(total_bytes), 0)) AS value FROM system.tables WHERE database = currentDatabase()", "value"),
                        queryItem(operation, dbName, "unfinished_mutations", "未完成 mutation",
                                "SELECT COUNT(*) AS value FROM system.mutations WHERE database = currentDatabase() AND is_done = 0", "value")
                ),
                section("extra", "其他信息",
                        queryItem(operation, dbName, "merges", "进行中 merges", "SELECT COUNT(*) AS value FROM system.merges", "value"),
                        queryItem(operation, dbName, "parts", "active parts",
                                "SELECT COUNT(*) AS value FROM system.parts WHERE database = currentDatabase() AND active = 1", "value"),
                        queryItem(operation, dbName, "disks", "磁盘数", "SELECT COUNT(*) AS value FROM system.disks", "value"),
                        queryItem(operation, dbName, "replicas", "副本数", "SELECT COUNT(*) AS value FROM system.replicas", "value")
                )
        );
    }
}
