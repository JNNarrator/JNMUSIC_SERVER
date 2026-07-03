package com.jn.music.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jn.music.common.config.TraceIdConfig;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.user.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HistoryControllerTest {

    private HistoryService historyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        historyService = Mockito.mock(HistoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HistoryController(historyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdConfig())
                .build();
    }

    @Test
    void recordAndClearHistory() throws Exception {
        mockMvc.perform(post("/api/v1/history")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":\"T1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
