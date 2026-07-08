package com.jn.music.track.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jn.music.common.PageResponse;
import com.jn.music.common.config.TraceIdConfig;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.service.TrackService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrackControllerTest {

    private TrackService trackService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        trackService = Mockito.mock(TrackService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackController(trackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdConfig())
                .build();
    }

    @Test
    void searchTracksReadsQueryKeywordAndNormalizesPagination() throws Exception {
        PageResponse<TrackSummaryDTO> page = PageResponse.<TrackSummaryDTO>builder()
                .items(List.of(TrackSummaryDTO.builder().trackId("T0000421").name("晴天").build()))
                .page(1)
                .pageSize(50)
                .total(1L)
                .hasMore(false)
                .build();
        when(trackService.searchTracks("晴天", 1, 50)).thenReturn(page);

        mockMvc.perform(get("/api/v1/tracks/search")
                        .param("q", "  晴天  ")
                        .param("page", "0")
                        .param("pageSize", "99"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", startsWith("req_")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId", startsWith("req_")))
                .andExpect(jsonPath("$.data.items[0].trackId").value("T0000421"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(50));

        verify(trackService).searchTracks("晴天", 1, 50);
    }

    @Test
    void batchTracksTrimsAndDeduplicatesIds() throws Exception {
        PageResponse<TrackDTO> page = PageResponse.<TrackDTO>builder()
                .items(List.of(TrackDTO.builder().trackId("T0000001").build()))
                .page(1)
                .pageSize(2)
                .total(1L)
                .hasMore(false)
                .build();
        when(trackService.getTracksByIds(List.of("T0000001", "T0000002"))).thenReturn(page);

        mockMvc.perform(get("/api/v1/tracks/batch")
                        .param("ids", " T0000001 ", "T0000002", "T0000001", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageSize").value(2));

        verify(trackService).getTracksByIds(List.of("T0000001", "T0000002"));
    }

    @Test
    void batchTracksSupportsCommaSeparatedIdsFromDocument() throws Exception {
        PageResponse<TrackDTO> page = PageResponse.<TrackDTO>builder()
                .items(List.of(TrackDTO.builder().trackId("T0000001").build()))
                .page(1)
                .pageSize(2)
                .total(1L)
                .hasMore(false)
                .build();
        when(trackService.getTracksByIds(List.of("T0000001", "T0000002"))).thenReturn(page);

        mockMvc.perform(get("/api/v1/tracks/batch")
                        .param("ids", " T0000001 , T0000002 , T0000001 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId", startsWith("req_")));

        verify(trackService).getTracksByIds(List.of("T0000001", "T0000002"));
    }

    @Test
    void mediaUrlReturnsPlayableUrlEnvelope() throws Exception {
        when(trackService.getMediaUrl("T0000421")).thenReturn(MediaUrlDTO.builder()
                .trackId("T0000421")
                .mediaUrl("http://media.example.com/audio/T0000421.flac")
                .format("flac")
                .expiresAt(OffsetDateTime.parse("2026-07-03T00:00:00Z"))
                .build());

        mockMvc.perform(get("/api/v1/tracks/T0000421/media-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackId").value("T0000421"))
                .andExpect(jsonPath("$.data.mediaUrl").value("http://media.example.com/audio/T0000421.flac"))
                .andExpect(jsonPath("$.data.format").value("flac"));
    }

    @Test
    void detailReturnsTrackNotFoundError() throws Exception {
        when(trackService.getTrackById("T404")).thenThrow(new BusinessException(ErrorCode.TRACK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/tracks/T404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TRACK_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId", startsWith("req_")));
    }
}
