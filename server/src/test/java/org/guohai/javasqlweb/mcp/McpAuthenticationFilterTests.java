package org.guohai.javasqlweb.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpAuthenticationFilterTests {

    private final UserService userService = mock(UserService.class);
    private final McpAuthenticationFilter filter = new McpAuthenticationFilter(userService, new ObjectMapper());

    @Test
    void rejectsMissingAndMalformedTokensBeforeAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("access token invalid"));
        verify(userService, never()).checkApiAccess(null, "Bearer wrong-token");
    }

    @Test
    void authenticatesValidTokenAndAddsTransportAttributes() throws Exception {
        String authorization = "Bearer jsw_0123456789abcdef0123456789abcdef01234567";
        UserBean user = new UserBean();
        user.setCode(7);
        user.setUserName("alice");
        when(userService.checkApiAccess(null, authorization))
                .thenReturn(new Result<>(true, "success", user));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", authorization);
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertSame(user, request.getAttribute(McpAuthenticationFilter.AUTHENTICATED_USER_ATTR));
        assertEquals("203.0.113.9", request.getAttribute(McpAuthenticationFilter.CLIENT_IP_ATTR));
        verify(userService).checkApiAccess(null, authorization);
    }

    @Test
    void reportsExpiredTokenWithoutExposingIt() throws Exception {
        String authorization = "Bearer jsw_0123456789abcdef0123456789abcdef01234567";
        when(userService.checkApiAccess(null, authorization))
                .thenReturn(new Result<>(false, "access token expired", null));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("access token expired"));
        assertTrue(!response.getContentAsString().contains("0123456789abcdef"));
    }
}
