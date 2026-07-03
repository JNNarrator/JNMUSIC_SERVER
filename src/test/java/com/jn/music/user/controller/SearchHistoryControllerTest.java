package com.jn.music.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jn.music.common.config.TraceIdConfig;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.user.service.SearchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SearchHistoryControllerTest {

    private SearchHistoryService searchHistoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchHistoryService = Mockito.mock(SearchHistoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchHistoryController(searchHistoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdConfig())
                .build();
    }

    @Test
    void recordAndClearSearchHistory() throws Exception {
        mockMvc.perform(post("/api/v1/search-history")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"周杰伦\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/v1/search-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
