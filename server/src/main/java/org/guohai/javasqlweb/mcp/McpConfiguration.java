package org.guohai.javasqlweb.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.BaseDataService;
import org.guohai.javasqlweb.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

@Configuration
public class McpConfiguration {

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper.copy());
    }

    @Bean
    public JswMcpTools jswMcpTools(BaseDataService baseDataService,
                                   ObjectMapper objectMapper,
                                   @Value("${project.mcp-query-limit:1000}") int queryLimit,
                                   @Value("${project.mcp-query-rate-per-minute:20}") int queryRatePerMinute) {
        return new JswMcpTools(baseDataService, objectMapper, queryLimit, queryRatePerMinute);
    }

    @Bean
    public HttpServletStatelessServerTransport mcpTransport(
            McpJsonMapper jsonMapper,
            @Value("${project.domain:localhost}") String projectDomain,
            @Value("${project.host:http://localhost}") String projectHost,
            @Value("${project.mcp-max-request-size:65536}") int maxRequestSize) {
        DefaultServerTransportSecurityValidator securityValidator =
                DefaultServerTransportSecurityValidator.builder()
                        .allowedHosts(List.of(projectDomain, "localhost", "127.0.0.1", "[::1]"))
                        .allowedOrigins(List.of(projectHost))
                        .build();
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint("/mcp")
                .maxRequestSize(Math.max(1024, maxRequestSize))
                .securityValidator(securityValidator)
                .contextExtractor(request -> {
                    UserBean user = (UserBean) request.getAttribute(McpAuthenticationFilter.AUTHENTICATED_USER_ATTR);
                    String clientIp = (String) request.getAttribute(McpAuthenticationFilter.CLIENT_IP_ATTR);
                    return JswMcpTools.transportContext(user, clientIp);
                })
                .build();
    }

    @Bean(destroyMethod = "close")
    public McpStatelessSyncServer mcpServer(HttpServletStatelessServerTransport transport,
                                            JswMcpTools tools,
                                            @Value("${project.version}") String version) {
        return McpServer.sync(transport)
                .serverInfo("java-sql-web", version)
                .instructions("Discover accessible servers and schema before executing minimal read-only SQL. Queries are audited and limited by server policy.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .requestTimeout(Duration.ofSeconds(35))
                .tools(tools.specifications())
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport, "/mcp");
        registration.setName("jswMcpServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<McpAuthenticationFilter> mcpAuthenticationFilter(
            UserService userService,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<McpAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new McpAuthenticationFilter(userService, objectMapper));
        registration.setName("jswMcpAuthenticationFilter");
        registration.addUrlPatterns("/mcp");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        registration.setOrder(Integer.MIN_VALUE + 100);
        return registration;
    }
}
