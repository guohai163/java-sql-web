package org.guohai.javasqlweb.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.guohai.javasqlweb.beans.ColumnsNameBean;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.TableIndexesBean;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.BaseDataService;
import org.guohai.javasqlweb.util.RateLimitUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JswMcpTools {

    private static final long RATE_LIMIT_WINDOW_MILLIS = 60_000L;
    private static final String USER_CONTEXT_KEY = "jsw.user";
    private static final String CLIENT_IP_CONTEXT_KEY = "jsw.clientIp";

    private final BaseDataService baseDataService;
    private final ObjectMapper objectMapper;
    private final int queryLimit;
    private final int queryRatePerMinute;

    public JswMcpTools(BaseDataService baseDataService,
                       ObjectMapper objectMapper,
                       int queryLimit,
                       int queryRatePerMinute) {
        this.baseDataService = baseDataService;
        this.objectMapper = objectMapper;
        this.queryLimit = Math.max(1, queryLimit);
        this.queryRatePerMinute = Math.max(1, queryRatePerMinute);
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("jsw_list_servers", "List database servers accessible to the authenticated JSW user.", emptySchema(), this::listServers),
                tool("jsw_list_databases", "List databases on an accessible JSW database server.", serverSchema(), this::listDatabases),
                tool("jsw_list_tables", "List tables and comments in an accessible database.", databaseSchema(), this::listTables),
                tool("jsw_describe_table", "Return columns and indexes for a table in an accessible database.", tableSchema(), this::describeTable),
                tool("jsw_query", "Execute read-only SQL through JSW. Results are limited and audited.", querySchema(), this::query)
        );
    }

    private McpStatelessServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            java.util.function.BiFunction<McpTransportContext, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        McpSchema.Tool definition = McpSchema.Tool.builder(name)
                .description(description)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema())
                .build();
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(definition)
                .callHandler(handler)
                .build();
    }

    private McpSchema.CallToolResult listServers(McpTransportContext context, McpSchema.CallToolRequest request) {
        UserBean user = requireUser(context);
        Result<List<ConnectConfigBean>> result = baseDataService.getHavaPermConn(user);
        if (!Boolean.TRUE.equals(result.getStatus())) {
            return failure(result.getMessage(), null, null);
        }
        List<Map<String, Object>> servers = result.getData() == null ? List.of() : result.getData().stream()
                .map(server -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("serverCode", server.getCode());
                    item.put("serverName", server.getDbServerName());
                    item.put("serverType", server.getDbServerType());
                    item.put("group", server.getDbGroup());
                    return item;
                })
                .toList();
        return success(null, null, servers, servers.size(), false, "");
    }

    private McpSchema.CallToolResult listDatabases(McpTransportContext context, McpSchema.CallToolRequest request) {
        Integer serverCode = requiredInteger(request, "serverCode");
        Result<?> result = baseDataService.getDbName(serverCode, requireUser(context));
        return fromResult(result, serverCode, null);
    }

    private McpSchema.CallToolResult listTables(McpTransportContext context, McpSchema.CallToolRequest request) {
        Integer serverCode = requiredInteger(request, "serverCode");
        String dbName = requiredString(request, "dbName");
        Result<?> result = baseDataService.getTableList(serverCode, dbName, requireUser(context));
        return fromResult(result, serverCode, dbName);
    }

    private McpSchema.CallToolResult describeTable(McpTransportContext context, McpSchema.CallToolRequest request) {
        Integer serverCode = requiredInteger(request, "serverCode");
        String dbName = requiredString(request, "dbName");
        String tableName = requiredString(request, "tableName");
        UserBean user = requireUser(context);
        Result<List<ColumnsNameBean>> columns = baseDataService.getColumnList(serverCode, dbName, tableName, user);
        if (!Boolean.TRUE.equals(columns.getStatus())) {
            return failure(columns.getMessage(), serverCode, dbName);
        }
        Result<List<TableIndexesBean>> indexes = baseDataService.getTableIndexes(serverCode, dbName, tableName, user);
        if (!Boolean.TRUE.equals(indexes.getStatus())) {
            return failure(indexes.getMessage(), serverCode, dbName);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", tableName);
        data.put("columns", columns.getData() == null ? List.of() : columns.getData());
        data.put("indexes", indexes.getData() == null ? List.of() : indexes.getData());
        return success(serverCode, dbName, data, null, false, "");
    }

    private McpSchema.CallToolResult query(McpTransportContext context, McpSchema.CallToolRequest request) {
        Integer serverCode = requiredInteger(request, "serverCode");
        String dbName = requiredString(request, "dbName");
        String sql = requiredString(request, "sql");
        UserBean user = requireUser(context);
        String rateLimitKey = user.getCode() == null ? user.getUserName() : user.getCode().toString();
        if (!RateLimitUtils.tryAcquire("mcpQuery", rateLimitKey, queryRatePerMinute, RATE_LIMIT_WINDOW_MILLIS)) {
            return failure("MCP query rate limit exceeded", serverCode, dbName);
        }
        Result<Object> result = baseDataService.queryDataBySql(
                serverCode,
                dbName,
                sql,
                user,
                stringContextValue(context, CLIENT_IP_CONTEXT_KEY),
                queryLimit
        );
        if (!Boolean.TRUE.equals(result.getStatus())) {
            return failure(result.getMessage(), serverCode, dbName);
        }
        int rowCount = result.getData() instanceof List<?> rows ? rows.size() : 0;
        boolean truncated = result.getMessage() != null && !result.getMessage().isBlank();
        return success(serverCode, dbName, result.getData(), rowCount, truncated, result.getMessage());
    }

    private McpSchema.CallToolResult fromResult(Result<?> result, Integer serverCode, String dbName) {
        if (!Boolean.TRUE.equals(result.getStatus())) {
            return failure(result.getMessage(), serverCode, dbName);
        }
        int rowCount = result.getData() instanceof List<?> rows ? rows.size() : 0;
        return success(serverCode, dbName, result.getData(), rowCount, false, result.getMessage());
    }

    private McpSchema.CallToolResult success(Integer serverCode,
                                             String dbName,
                                             Object data,
                                             Integer rowCount,
                                             boolean truncated,
                                             String message) {
        Map<String, Object> payload = basePayload(true, serverCode, dbName, message);
        payload.put("data", data);
        if (rowCount != null) {
            payload.put("rowCount", rowCount);
        }
        payload.put("truncated", truncated);
        payload.put("limit", queryLimit);
        return response(payload, false);
    }

    private McpSchema.CallToolResult failure(String message, Integer serverCode, String dbName) {
        Map<String, Object> payload = basePayload(false, serverCode, dbName, safeMessage(message));
        payload.put("data", null);
        return response(payload, true);
    }

    private Map<String, Object> basePayload(boolean status, Integer serverCode, String dbName, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (serverCode != null) {
            payload.put("serverCode", serverCode);
        }
        if (dbName != null) {
            payload.put("dbName", dbName);
        }
        payload.put("message", message == null ? "" : message);
        return payload;
    }

    private McpSchema.CallToolResult response(Map<String, Object> payload, boolean error) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(payload))
                    .structuredContent(payload)
                    .isError(error)
                    .build();
        } catch (JsonProcessingException exception) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Unable to serialize MCP tool result")
                    .isError(true)
                    .build();
        }
    }

    private UserBean requireUser(McpTransportContext context) {
        Object user = context.get(USER_CONTEXT_KEY);
        if (user instanceof UserBean userBean) {
            return userBean;
        }
        throw new IllegalStateException("MCP request is not authenticated");
    }

    private Integer requiredInteger(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(name + " must be an integer");
    }

    private String requiredString(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value instanceof String string && !string.isBlank()) {
            return string.trim();
        }
        throw new IllegalArgumentException(name + " must be a non-empty string");
    }

    private String stringContextValue(McpTransportContext context, String key) {
        Object value = context.get(key);
        return value == null ? "" : value.toString();
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "JSW request failed";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private Map<String, Object> outputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "status", Map.of("type", "boolean"),
                "serverCode", Map.of("type", "integer"),
                "dbName", Map.of("type", "string"),
                "data", Map.of(),
                "rowCount", Map.of("type", "integer"),
                "truncated", Map.of("type", "boolean"),
                "limit", Map.of("type", "integer"),
                "message", Map.of("type", "string")
        ));
        schema.put("required", List.of("status", "data", "message"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> emptySchema() {
        return objectSchema(Map.of(), List.of());
    }

    private Map<String, Object> serverSchema() {
        return objectSchema(Map.of("serverCode", integerProperty("JSW database server code")), List.of("serverCode"));
    }

    private Map<String, Object> databaseSchema() {
        return objectSchema(Map.of(
                "serverCode", integerProperty("JSW database server code"),
                "dbName", stringProperty("Database name returned by jsw_list_databases")
        ), List.of("serverCode", "dbName"));
    }

    private Map<String, Object> tableSchema() {
        return objectSchema(Map.of(
                "serverCode", integerProperty("JSW database server code"),
                "dbName", stringProperty("Database name returned by jsw_list_databases"),
                "tableName", stringProperty("Table name returned by jsw_list_tables")
        ), List.of("serverCode", "dbName", "tableName"));
    }

    private Map<String, Object> querySchema() {
        return objectSchema(Map.of(
                "serverCode", integerProperty("JSW database server code"),
                "dbName", stringProperty("Database name returned by jsw_list_databases"),
                "sql", stringProperty("Read-only SQL to execute")
        ), List.of("serverCode", "dbName", "sql"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    static McpTransportContext transportContext(UserBean user, String clientIp) {
        return McpTransportContext.create(Map.of(
                USER_CONTEXT_KEY, user,
                CLIENT_IP_CONTEXT_KEY, clientIp == null ? "" : clientIp
        ));
    }
}
