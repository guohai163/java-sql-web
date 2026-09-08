package org.guohai.javasqlweb.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.BaseDataService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JswMcpToolsTests {

    private final BaseDataService baseDataService = mock(BaseDataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesExpectedTools() {
        JswMcpTools tools = new JswMcpTools(baseDataService, objectMapper, 1000, 20);

        assertEquals(
                List.of("jsw_list_servers", "jsw_list_databases", "jsw_list_tables", "jsw_describe_table", "jsw_query"),
                tools.specifications().stream().map(specification -> specification.tool().name()).toList()
        );
    }

    @Test
    void listServersReturnsOnlySanitizedFields() {
        UserBean user = user(101);
        ConnectConfigBean server = new ConnectConfigBean();
        server.setCode(9);
        server.setDbServerName("analytics");
        server.setDbServerType("postgresql");
        server.setDbGroup("production");
        server.setDbServerHost("secret.internal");
        server.setDbServerUsername("db-user");
        server.setDbServerPassword("db-password");
        when(baseDataService.getHavaPermConn(user)).thenReturn(new Result<>(true, "", List.of(server)));
        JswMcpTools tools = new JswMcpTools(baseDataService, objectMapper, 1000, 20);

        McpSchema.CallToolResult result = call(tools, "jsw_list_servers", user, Map.of());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertTrue(text.contains("analytics"));
        assertFalse(text.contains("secret.internal"));
        assertFalse(text.contains("db-user"));
        assertFalse(text.contains("db-password"));
    }

    @Test
    void queryUsesMcpLimitAndAuthenticatedIdentity() {
        UserBean user = user(102);
        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(baseDataService.queryDataBySql(9, "analytics", "SELECT id FROM orders", user, "203.0.113.9", 1000))
                .thenReturn(new Result<>(true, "", rows));
        JswMcpTools tools = new JswMcpTools(baseDataService, objectMapper, 1000, 20);

        McpSchema.CallToolResult result = call(tools, "jsw_query", user, Map.of(
                "serverCode", 9,
                "dbName", "analytics",
                "sql", "SELECT id FROM orders"
        ));

        assertFalse(Boolean.TRUE.equals(result.isError()));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.structuredContent();
        assertEquals(1, payload.get("rowCount"));
        assertEquals(1000, payload.get("limit"));
        assertEquals(false, payload.get("truncated"));
        verify(baseDataService).queryDataBySql(9, "analytics", "SELECT id FROM orders", user, "203.0.113.9", 1000);
    }

    @Test
    void queryReturnsPermissionFailureAsToolError() {
        UserBean user = user(103);
        when(baseDataService.queryDataBySql(eq(99), eq("secret"), any(), eq(user), any(), eq(1000)))
                .thenReturn(new Result<>(false, "无权限访问该数据库服务器", null));
        JswMcpTools tools = new JswMcpTools(baseDataService, objectMapper, 1000, 20);

        McpSchema.CallToolResult result = call(tools, "jsw_query", user, Map.of(
                "serverCode", 99,
                "dbName", "secret",
                "sql", "SELECT 1"
        ));

        assertTrue(Boolean.TRUE.equals(result.isError()));
        assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("无权限"));
    }

    @Test
    void queryRateLimitStopsRequestBeforeDatabaseCall() {
        UserBean user = user(987654);
        when(baseDataService.queryDataBySql(any(), any(), any(), any(), any(), any()))
                .thenReturn(new Result<>(true, "", List.of()));
        JswMcpTools tools = new JswMcpTools(baseDataService, objectMapper, 1000, 1);
        Map<String, Object> arguments = Map.of("serverCode", 9, "dbName", "analytics", "sql", "SELECT 1");

        call(tools, "jsw_query", user, arguments);
        McpSchema.CallToolResult rejected = call(tools, "jsw_query", user, arguments);

        assertTrue(Boolean.TRUE.equals(rejected.isError()));
        assertTrue(((McpSchema.TextContent) rejected.content().get(0)).text().contains("rate limit"));
        verify(baseDataService).queryDataBySql(9, "analytics", "SELECT 1", user, "203.0.113.9", 1000);
    }

    private McpSchema.CallToolResult call(JswMcpTools tools,
                                          String toolName,
                                          UserBean user,
                                          Map<String, Object> arguments) {
        McpStatelessServerFeatures.SyncToolSpecification specification = tools.specifications().stream()
                .filter(candidate -> candidate.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow();
        McpTransportContext context = JswMcpTools.transportContext(user, "203.0.113.9");
        return specification.callHandler().apply(context, new McpSchema.CallToolRequest(toolName, arguments));
    }

    private UserBean user(int code) {
        UserBean user = new UserBean();
        user.setCode(code);
        user.setUserName("user-" + code);
        return user;
    }
}
