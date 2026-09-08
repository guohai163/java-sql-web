package org.guohai.javasqlweb.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.UserService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

public class McpAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_ATTR = McpAuthenticationFilter.class.getName() + ".user";
    public static final String CLIENT_IP_ATTR = McpAuthenticationFilter.class.getName() + ".clientIp";
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("^Bearer jsw_[0-9a-fA-F]{40}$");

    private final UserService userService;
    private final ObjectMapper objectMapper;

    public McpAuthenticationFilter(UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !ACCESS_TOKEN_PATTERN.matcher(authorization).matches()) {
            writeUnauthorized(response, "access token invalid");
            return;
        }

        Result<UserBean> authentication = userService.checkApiAccess(null, authorization);
        if (!Boolean.TRUE.equals(authentication.getStatus()) || authentication.getData() == null) {
            writeUnauthorized(response, authentication.getMessage());
            return;
        }

        request.setAttribute(AUTHENTICATED_USER_ATTR, authentication.getData());
        request.setAttribute(CLIENT_IP_ATTR, resolveClientIp(request));
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstAddress = forwardedFor.split(",", 2)[0].trim();
            if (!firstAddress.isEmpty()) {
                return firstAddress;
            }
        }
        return request.getRemoteAddr();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(objectMapper.writeValueAsString(
                new Result<>(false, message == null || message.isBlank() ? "access token invalid" : message, null)
        ));
    }
}
