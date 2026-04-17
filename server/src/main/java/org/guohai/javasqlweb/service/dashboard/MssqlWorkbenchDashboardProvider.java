package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.util.DbServerTypeUtils;

import java.util.List;

public class MssqlWorkbenchDashboardProvider extends AbstractWorkbenchDashboardProvider {

    @Override
    public String getDbType() {
        return DbServerTypeUtils.MSSQL;
    }

    @Override
    public List<WorkbenchDashboardSection> buildSections(DbOperation operation, String dbName, ConnectConfigBean config) {
        return List.of(
                section("system", "系统信息",
                        staticItem("server_name", "实例名称", config.getDbServerName()),
                        staticItem("server_host", "服务器地址", config.getDbServerHost()),
                        queryItem(operation, dbName, "version", "版本", "SELECT @@VERSION AS value", "value"),
                        queryItem(operation, dbName, "edition", "Edition", "SELECT CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS value", "value"),
                        queryItem(operation, dbName, "product_version", "ProductVersion", "SELECT CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS value", "value"),
                        queryItem(operation, dbName, "start_time", "启动时间", "SELECT sqlserver_start_time AS value FROM sys.dm_os_sys_info", "value"),
                        queryItem(operation, dbName, "session_count", "当前会话数", "SELECT COUNT(*) AS value FROM sys.dm_exec_sessions", "value"),
                        queryItem(operation, dbName, "active_requests", "活动请求数", "SELECT COUNT(*) AS value FROM sys.dm_exec_requests WHERE session_id > 50", "value")
                ),
                section("database", "数据库信息",
                        staticItem("database_name", "当前数据库", dbName),
                        queryItem(operation, dbName, "database_size_mb", "库大小(MB)", "SELECT CAST(SUM(size) * 8.0 / 1024 AS decimal(18,2)) AS value FROM sys.database_files", "value"),
                        queryItem(operation, dbName, "compatibility_level", "兼容级别", "SELECT compatibility_level AS value FROM sys.databases WHERE name = DB_NAME()", "value"),
                        queryItem(operation, dbName, "recovery_model", "恢复模式", "SELECT recovery_model_desc AS value FROM sys.databases WHERE name = DB_NAME()", "value"),
                        queryItem(operation, dbName, "table_count", "用户表数量", "SELECT COUNT(*) AS value FROM sys.tables", "value"),
                        queryItem(operation, dbName, "long_running_requests", "长请求数(>5s)",
                                "SELECT COUNT(*) AS value FROM sys.dm_exec_requests WHERE total_elapsed_time >= 5000 AND session_id > 50", "value")
                ),
                section("extra", "其他信息",
                        queryItem(operation, dbName, "waiting_tasks", "等待任务数", "SELECT COUNT(*) AS value FROM sys.dm_os_waiting_tasks", "value"),
                        queryItem(operation, dbName, "available_memory_mb", "可用物理内存(MB)",
                                "SELECT available_physical_memory_kb / 1024 AS value FROM sys.dm_os_sys_memory", "value"),
                        queryItem(operation, dbName, "online_schedulers", "在线调度器", "SELECT COUNT(*) AS value FROM sys.dm_os_schedulers WHERE status = 'VISIBLE ONLINE'", "value"),
                        queryItem(operation, dbName, "tempdb_files", "TempDB 文件数", "SELECT COUNT(*) AS value FROM tempdb.sys.database_files", "value")
                )
        );
    }
}
