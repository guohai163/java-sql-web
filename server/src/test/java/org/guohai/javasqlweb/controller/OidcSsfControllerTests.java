package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.OidcSsfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OidcSsfControllerTests {

    @Mock
    private OidcSsfService oidcSsfService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OidcSsfController controller = new OidcSsfController();
        ReflectionTestUtils.setField(controller, "oidcSsfService", oidcSsfService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void loginCallbackShouldRedirectToLoginNoticeWhenAuthorizationIsDenied() throws Exception {
        mockMvc.perform(get("/api/oidc/login/callback")
                        .param("error", "access_denied")
                        .param("error_description", "用户已拒绝了授权请求")
                        .param("state", "oidc-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?oidc_error=access_denied&oidc_error_description=%E7%94%A8%E6%88%B7%E5%B7%B2%E6%8B%92%E7%BB%9D%E4%BA%86%E6%8E%88%E6%9D%83%E8%AF%B7%E6%B1%82"));

        verify(oidcSsfService, never()).handleLoginCallback(eq(""), eq("oidc-state"), any());
    }

    @Test
    void loginCallbackShouldUseFriendlyFallbackWhenProviderDoesNotReturnDescription() throws Exception {
        mockMvc.perform(get("/api/oidc/login/callback")
                        .param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?oidc_error=access_denied&oidc_error_description=%E4%BD%A0%E5%B7%B2%E5%8F%96%E6%B6%88%E6%9C%AC%E6%AC%A1+OIDC+%E6%8E%88%E6%9D%83%E7%99%BB%E5%BD%95"));
    }

    @Test
    void loginCallbackShouldKeepSuccessRedirectBehavior() throws Exception {
        UserBean user = new UserBean();
        user.setToken("oidc-token");
        user.setAuthStatus(OtpAuthStatus.BINDING);
        user.setAuthSecret("secret-123");
        user.setUserName("guohai");
        when(oidcSsfService.handleLoginCallback(eq("auth-code"), eq("oidc-state"), any()))
                .thenReturn(new Result<>(true, "OK", user));

        mockMvc.perform(get("/api/oidc/login/callback")
                        .param("code", "auth-code")
                        .param("state", "oidc-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?oidc_token=oidc-token&auth_status=BINDING&auth_secret=secret-123&user_name=guohai"));

        verify(oidcSsfService).handleLoginCallback(eq("auth-code"), eq("oidc-state"), any());
    }
}
