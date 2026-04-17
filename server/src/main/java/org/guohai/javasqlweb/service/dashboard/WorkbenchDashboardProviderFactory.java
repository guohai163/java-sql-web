package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.util.DbServerTypeUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkbenchDashboardProviderFactory {

    private static final Map<String, WorkbenchDashboardProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        register(new MysqlWorkbenchDashboardProvider());
        register(new MariaDbWorkbenchDashboardProvider());
        register(new PostgresqlWorkbenchDashboardProvider());
        register(new MssqlWorkbenchDashboardProvider());
        register(new ClickHouseWorkbenchDashboardProvider());
    }

    private WorkbenchDashboardProviderFactory() {
    }

    public static WorkbenchDashboardProvider getProvider(String dbType) {
        return PROVIDERS.get(DbServerTypeUtils.normalize(dbType));
    }

    private static void register(WorkbenchDashboardProvider provider) {
        PROVIDERS.put(provider.getDbType(), provider);
    }
}
