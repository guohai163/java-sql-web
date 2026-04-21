package org.guohai.javasqlweb.service.dashboard;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.WorkbenchDashboardSection;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickHouseWorkbenchDashboardProviderTests {

    @Test
    void buildSectionsKeepsSelectedDatabaseForCurrentDatabaseQueries() throws Exception {
        ClickHouseWorkbenchDashboardProvider provider = new ClickHouseWorkbenchDashboardProvider();
        DbOperation operation = mock(DbOperation.class);
        ConnectConfigBean config = new ConnectConfigBean();
        config.setDbServerType("clickhouse");
        config.setDbServerName("ck-prod");
        config.setDbServerHost("127.0.0.1");

        when(operation.queryDatabaseBySql(anyString(), anyString(), anyInt()))
                .thenReturn(new Object[]{1, 1, List.of(Map.of("value", "1"))});

        List<WorkbenchDashboardSection> sections = provider.buildSections(operation, "analytics", config);

        assertTrue(sections.stream().anyMatch(section -> "database".equals(section.getKey())));
        ArgumentCaptor<String> dbCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(operation, org.mockito.Mockito.atLeastOnce()).queryDatabaseBySql(dbCaptor.capture(), sqlCaptor.capture(), anyInt());
        boolean matched = false;
        for (int index = 0; index < sqlCaptor.getAllValues().size(); index++) {
            String sql = sqlCaptor.getAllValues().get(index);
            if (sql != null && sql.contains("currentDatabase()")) {
                matched = true;
                org.junit.jupiter.api.Assertions.assertEquals("analytics", dbCaptor.getAllValues().get(index));
            }
        }
        assertTrue(matched);
    }
}
