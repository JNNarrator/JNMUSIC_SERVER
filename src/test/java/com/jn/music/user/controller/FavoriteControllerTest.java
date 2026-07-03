package com.jn.music.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jn.music.common.PageResponse;
import com.jn.music.common.config.TraceIdConfig;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.service.TrackService;
import com.jn.music.user.service.FavoriteService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FavoriteControllerTest {

    private FavoriteService favoriteService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        favoriteService = Mockito.mock(FavoriteService.class);
        TrackService trackService = Mockito.mock(TrackService.class);
        Mockito.when(trackService.getTrackById("T1")).thenReturn(TrackDTO.builder().trackId("T1").build());
        mockMvc = MockMvcBuilders.standaloneSetup(new FavoriteController(favoriteService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdConfig())
                .build();
    }

    @Test
    void listFavoritesUsesDeviceHeader() throws Exception {
        when(favoriteService.listFavorites("device-1", 1, 20))
                .thenReturn(PageResponse.<TrackDTO>builder()
                        .items(List.of(TrackDTO.builder().trackId("T1").build()))
                        .page(1)
                        .pageSize(20)
                        .total(1L)
                        .hasMore(false)
                        .build());

        mockMvc.perform(get("/api/v1/favorites")
                        .header("X-Device-Id", "device-1")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].trackId").value("T1"));

        verify(favoriteService).listFavorites("device-1", 1, 20);
    }
}
