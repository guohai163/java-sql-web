package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.service.BaseDataService;
import org.guohai.javasqlweb.service.ProbeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HomeControllerTests {

    @Mock
    private BaseDataService baseDataService;

    @Mock
    private ProbeService probeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HomeController controller = new HomeController();
        ReflectionTestUtils.setField(controller, "baseService", baseDataService);
        ReflectionTestUtils.setField(controller, "probeService", probeService);
        ReflectionTestUtils.setField(controller, "version", "2.7.1");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void livezShouldReturnOkAndNotCallBusinessHealth() throws Exception {
        when(probeService.checkLiveness()).thenReturn(new Result<>(true, "", "alive"));

        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data").value("alive"));

        verify(probeService).checkLiveness();
        verifyNoInteractions(baseDataService);
    }

    @Test
    void readyzShouldReturnOkWhenProbeServiceSucceeds() throws Exception {
        when(probeService.checkReadiness()).thenReturn(new Result<>(true, "", "ready"));

        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data").value("ready"));
    }

    @Test
    void readyzShouldReturnServiceUnavailableWhenProbeServiceFails() throws Exception {
        when(probeService.checkReadiness()).thenReturn(new Result<>(false, "main db down", "main db down"));

        mockMvc.perform(get("/readyz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("main db down"));
    }

    @Test
    void healthShouldKeepReturning500WhenBusinessHealthFails() throws Exception {
        when(baseDataService.serverHealth()).thenReturn(new Result<>(false, "巡检失败", "巡检失败"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("巡检失败"));
    }
}
