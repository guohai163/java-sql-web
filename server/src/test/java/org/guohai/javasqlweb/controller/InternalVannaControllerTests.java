package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.DatabaseNameBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.VannaContextResponse;
import org.guohai.javasqlweb.beans.VannaServerWarmupItem;
import org.guohai.javasqlweb.config.AuthenticationInterceptor;
import org.guohai.javasqlweb.service.BaseDataService;
import org.guohai.javasqlweb.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalVannaControllerTests {

    @Mock
    private BaseDataService baseDataService;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalVannaController controller = new InternalVannaController();
        ReflectionTestUtils.setField(controller, "baseDataService", baseDataService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "vannaInternalToken", "secret-token");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldRejectWhenInternalTokenMissing() throws Exception {
        mockMvc.perform(get("/internal/vanna/context/9/demo_db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("invalid internal token"));
    }

    @Test
    void shouldRejectWhenUserMissing() throws Exception {
        when(userService.checkApiAccess(null, null)).thenReturn(new Result<>(false, "not logged in", null));
        mockMvc.perform(get("/internal/vanna/context/9/demo_db")
                        .header("X-Vanna-Internal-Token", "secret-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("not logged in"));
    }

    @Test
    void shouldReturnContextWhenAuthorized() throws Exception {
        VannaContextResponse response = new VannaContextResponse();
        response.setDbName("demo_db");
        response.setContextVersion("v1");
        when(userService.checkApiAccess("user-token", null))
                .thenReturn(new Result<>(true, "success", authenticatedUser()));
        when(baseDataService.getVannaContext(9, "demo_db", authenticatedUser()))
                .thenReturn(new Result<>(true, "success", response));

        mockMvc.perform(get("/internal/vanna/context/9/demo_db")
                        .header("X-Vanna-Internal-Token", "secret-token")
                        .header("User-Token", "user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.dbName").value("demo_db"))
                .andExpect(jsonPath("$.data.contextVersion").value("v1"));

        verify(baseDataService).getVannaContext(9, "demo_db", authenticatedUser());
    }

    @Test
    void shouldReturnWarmupContextWithoutUserLogin() throws Exception {
        VannaContextResponse response = new VannaContextResponse();
        response.setDbName("warmup_db");
        response.setContextVersion("warmup-v1");
        when(baseDataService.getVannaWarmupContext(9, "warmup_db"))
                .thenReturn(new Result<>(true, "success", response));

        mockMvc.perform(get("/internal/vanna/context/9/warmup_db")
                        .header("X-Vanna-Internal-Token", "secret-token")
                        .header("X-Vanna-Warmup", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.dbName").value("warmup_db"))
                .andExpect(jsonPath("$.data.contextVersion").value("warmup-v1"));
    }

    @Test
    void shouldReturnWarmupServers() throws Exception {
        when(baseDataService.getVannaWarmupServers())
                .thenReturn(new Result<>(true, "success", java.util.List.of(
                        new VannaServerWarmupItem(9, "demo", "postgresql")
                )));

        mockMvc.perform(get("/internal/vanna/servers")
                        .header("X-Vanna-Internal-Token", "secret-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].serverCode").value(9))
                .andExpect(jsonPath("$.data[0].serverName").value("demo"));
    }

    @Test
    void shouldReturnWarmupDatabases() throws Exception {
        when(baseDataService.getVannaWarmupDatabases(9))
                .thenReturn(new Result<>(true, "success", java.util.List.of(
                        new DatabaseNameBean("demo_db")
                )));

        mockMvc.perform(get("/internal/vanna/databases/9")
                        .header("X-Vanna-Internal-Token", "secret-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].dbName").value("demo_db"));
    }

    private UserBean authenticatedUser() {
        UserBean user = new UserBean();
        user.setCode(1);
        user.setUserName("alice");
        return user;
    }
}
