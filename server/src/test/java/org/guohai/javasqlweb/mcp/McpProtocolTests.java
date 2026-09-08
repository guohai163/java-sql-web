package org.guohai.javasqlweb.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.service.BaseDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProtocolTests {

    private HttpServletStatelessServerTransport transport;
    private McpStatelessSyncServer server;
    private BaseDataService baseDataService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        baseDataService = mock(BaseDataService.class);
        JswMcpTools tools = new JswMcpTools(baseDataService, objectMapper, 1000, 20);
        transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint("/mcp")
                .securityValidator(DefaultServerTransportSecurityValidator.builder()
                        .allowedHosts(List.of("localhost"))
                        .allowedOrigins(List.of("http://localhost"))
                        .build())
                .contextExtractor(request -> JswMcpTools.transportContext(user(), "127.0.0.1"))
                .build();
        server = McpServer.sync(transport)
                .serverInfo("java-sql-web", "test")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void initializesAndListsToolsWithoutSessionState() throws Exception {
        MockHttpServletResponse initialize = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                """);
        assertEquals(200, initialize.getStatus());
        assertTrue(initialize.getContentAsString().contains("java-sql-web"));

        MockHttpServletResponse tools = post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);
        assertEquals(200, tools.getStatus());
        assertTrue(tools.getContentAsString().contains("jsw_list_servers"));
        assertTrue(tools.getContentAsString().contains("jsw_query"));
    }

    @Test
    void unknownMethodReturnsJsonRpcErrorInsteadOfHttpFailure() throws Exception {
        MockHttpServletResponse response = post("""
                {"jsonrpc":"2.0","id":3,"method":"server/discover","params":{}}
                """);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("-32601"));
    }

    @Test
    void callsToolAndReturnsStructuredContent() throws Exception {
        when(baseDataService.getHavaPermConn(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Result<>(true, "", List.of()));

        MockHttpServletResponse response = post("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"jsw_list_servers","arguments":{}}}
                """);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("structuredContent"));
        assertTrue(response.getContentAsString().contains("\"status\":true"));
    }

    @Test
    void getAndDeleteAreRejected() throws Exception {
        MockHttpServletRequest get = request("GET");
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        transport.service(get, getResponse);
        assertEquals(405, getResponse.getStatus());

        MockHttpServletRequest delete = request("DELETE");
        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        transport.service(delete, deleteResponse);
        assertEquals(405, deleteResponse.getStatus());
    }

    private MockHttpServletResponse post(String content) throws Exception {
        MockHttpServletRequest request = request("POST");
        request.setContentType("application/json");
        request.addHeader("Accept", "application/json, text/event-stream");
        request.setContent(content.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        transport.service(request, response);
        return response;
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/mcp");
        request.setServerName("localhost");
        request.addHeader("Host", "localhost");
        return request;
    }

    private UserBean user() {
        UserBean user = new UserBean();
        user.setCode(1);
        user.setUserName("alice");
        return user;
    }
}
