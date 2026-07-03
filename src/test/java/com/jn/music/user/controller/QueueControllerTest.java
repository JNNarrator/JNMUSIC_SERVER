package com.jn.music.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jn.music.common.config.TraceIdConfig;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.user.dto.SaveQueueRequest;
import com.jn.music.user.dto.TrackIdRequest;
import com.jn.music.user.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QueueControllerTest {

    private QueueService queueService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queueService = Mockito.mock(QueueService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QueueController(queueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdConfig())
                .build();
    }

    @Test
    void queueOperations() throws Exception {
        mockMvc.perform(get("/api/v1/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(put("/api/v1/queue")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"trackId\":\"T1\",\"position\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/queue/items")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":\"T1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/v1/queue/items/T1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
