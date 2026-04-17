package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.util.DbServerTypeUtils;

import java.util.List;

public class MariaDbWorkbenchDashboardProvider extends AbstractWorkbenchDashboardProvider {

    @Override
    public String getDbType() {
        return DbServerTypeUtils.MARIADB;
    }

    @Override
    public List<WorkbenchDashboardSection> buildSections(DbOperation operation, String dbName, ConnectConfigBean config) {
        return List.of(
                section("system", "系统信息",
                        staticItem("server_name", "实例名称", config.getDbServerName()),
                        staticItem("server_host", "服务器地址", config.getDbServerHost()),
                        queryItem(operation, dbName, "version", "MariaDB 版本", "SELECT VERSION() AS value", "value"),
                        queryItem(operation, dbName, "version_comment", "发行说明", "SELECT @@version_comment AS value", "value"),
                        queryItem(operation, dbName, "hostname", "主机名", "SELECT @@hostname AS value", "value"),
                        queryItem(operation, dbName, "uptime", "运行时长(秒)", "SHOW GLOBAL STATUS LIKE 'Uptime'", "Value"),
                        queryItem(operation, dbName, "max_connections", "最大连接数", "SHOW VARIABLES LIKE 'max_connections'", "Value"),
                        queryItem(operation, dbName, "threads_connected", "当前连接数", "SHOW GLOBAL STATUS LIKE 'Threads_connected'", "Value")
                ),
                section("database", "数据库信息",
                        staticItem("database_name", "当前数据库", dbName),
                        queryItem(operation, dbName, "database_size", "库大小(MB)",
                                "SELECT ROUND(COALESCE(SUM(data_length + index_length), 0) / 1024 / 1024, 2) AS value " +
                                        "FROM information_schema.tables WHERE table_schema = DATABASE()", "value"),
                        queryItem(operation, dbName, "table_count", "表数量",
                                "SELECT COUNT(*) AS value FROM information_schema.tables WHERE table_schema = DATABASE()", "value"),
                        queryItem(operation, dbName, "slow_queries", "慢查询累计", "SHOW GLOBAL STATUS LIKE 'Slow_queries'", "Value"),
                        queryItem(operation, dbName, "aborted_connects", "异常连接累计", "SHOW GLOBAL STATUS LIKE 'Aborted_connects'", "Value")
                ),
                section("extra", "其他信息",
                        queryItem(operation, dbName, "datadir", "数据目录", "SELECT @@datadir AS value", "value"),
                        queryItem(operation, dbName, "character_set_server", "服务端字符集", "SHOW VARIABLES LIKE 'character_set_server'", "Value"),
                        queryItem(operation, dbName, "collation_server", "服务端排序规则", "SHOW VARIABLES LIKE 'collation_server'", "Value"),
                        queryItem(operation, dbName, "open_tables", "打开表数量", "SHOW GLOBAL STATUS LIKE 'Open_tables'", "Value")
                )
        );
    }
}
