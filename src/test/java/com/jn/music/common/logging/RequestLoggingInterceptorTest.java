package com.jn.music.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jn.music.common.config.TraceIdConfig;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.track.controller.TrackController;
import com.jn.music.track.service.TrackService;
import com.jn.music.track.service.TrackCacheService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestLoggingInterceptorTest {

    @AfterEach
    void clearTraceId() {
        com.jn.music.common.TraceIdContext.clearTraceId();
    }

    @Test
    void shouldLogGetRequest() throws Exception {
        TrackService trackService = org.mockito.Mockito.mock(TrackService.class);
        TrackCacheService cacheService = org.mockito.Mockito.mock(TrackCacheService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrackController(trackService, cacheService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RequestLoggingInterceptor())
                .addFilters(new TraceIdConfig())
                .build();

        try (LogCapture capture = LogCapture.create("REQUEST")) {
            mockMvc.perform(get("/api/v1/tracks/search")
                            .param("q", "晴天"))
                    .andExpect(status().isOk());

            List<ch.qos.logback.classic.spi.ILoggingEvent> events = capture.events();
            assertThat(events).hasSize(2);
            assertThat(events.get(0).getFormattedMessage()).contains("REQUEST_START");
            assertThat(events.get(0).getFormattedMessage()).contains("method=GET");
            assertThat(events.get(0).getFormattedMessage()).contains("uri=/api/v1/tracks/search");
            assertThat(events.get(1).getFormattedMessage()).contains("REQUEST_END");
            assertThat(events.get(1).getFormattedMessage()).contains("status=200");
        }
    }

    @Test
    void shouldLogPostRequestBodySummary() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RequestLoggingInterceptor())
                .addFilters(new TraceIdConfig())
                .build();

        try (LogCapture capture = LogCapture.create("REQUEST")) {
            mockMvc.perform(post("/test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"trackId\":\"T1\"}"))
                    .andExpect(status().isOk());

            List<ch.qos.logback.classic.spi.ILoggingEvent> events = capture.events();
            assertThat(events.get(0).getFormattedMessage()).contains("REQUEST_START");
            assertThat(events.get(0).getFormattedMessage()).contains("method=POST");
            assertThat(events.get(0).getFormattedMessage()).contains("uri=/test");
            assertThat(events.get(0).getFormattedMessage()).contains("contentType=application/json");
        }
    }

    @Test
    void shouldLogFailedRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RequestLoggingInterceptor())
                .addFilters(new TraceIdConfig())
                .build();

        try (LogCapture capture = LogCapture.create("REQUEST")) {
            mockMvc.perform(get("/test/fail"))
                    .andExpect(status().isInternalServerError());

            List<ch.qos.logback.classic.spi.ILoggingEvent> events = capture.events();
            assertThat(events).hasSize(2);
            assertThat(events.get(1).getFormattedMessage()).contains("REQUEST_END");
            assertThat(events.get(1).getFormattedMessage()).contains("status=500");
            assertThat(events.get(1).getFormattedMessage()).contains("error=500");
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/test")
        public String accept() {
            return "ok";
        }

        @GetMapping("/test/fail")
        public String fail() {
            throw new IllegalStateException("intentional failure");
        }
    }
}
