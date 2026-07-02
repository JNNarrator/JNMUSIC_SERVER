package com.jn.music.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jn.music.admin.dto.AdminTrackRequest;
import com.jn.music.admin.dto.AdminUploadResponse;
import com.jn.music.admin.service.AdminTokenStore;
import com.jn.music.common.ApiResponse;
import com.jn.music.track.domain.Track;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.mapper.TrackMapper;
import com.jn.music.track.service.TrackService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class AdminTrackControllerTest {

    private TrackService trackService;
    private TrackMapper trackMapper;
    private AdminTokenStore tokenStore;
    private AdminTrackController controller;
    private List<String> forwardedPaths;

    @BeforeEach
    void setUp() {
        trackService = mock(TrackService.class);
        trackMapper = mock(TrackMapper.class);
        tokenStore = mock(AdminTokenStore.class);
        forwardedPaths = new ArrayList<>();
        controller = new AdminTrackController(
                trackService,
                trackMapper,
                tokenStore,
                (path, file) -> forwardedPaths.add(path));
        when(tokenStore.isValid("token", "admin")).thenReturn(true);
    }

    @Test
    void saveGeneratesTrackIdAndDefaultsBlankArtist() {
        when(trackMapper.insert(any(Track.class))).thenReturn(1);

        AdminTrackRequest request = new AdminTrackRequest();
        request.setName("Song A");
        request.setArtist(" ");
        request.setDuration(123);
        request.setFormat("mp3");

        ResponseEntity<ApiResponse<Track>> response = controller.save(null, "token", "admin", request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Track saved = response.getBody().getData();
        assertThat(saved.getTrackId()).matches("T[0-9a-f]{8}");
        assertThat(saved.getArtist()).isEqualTo("未知");

        ArgumentCaptor<Track> captor = ArgumentCaptor.forClass(Track.class);
        verify(trackMapper).insert(captor.capture());
        assertThat(captor.getValue().getTrackId()).isEqualTo(saved.getTrackId());
        assertThat(captor.getValue().getArtist()).isEqualTo("未知");
    }

    @Test
    void uploadKeepsExistingTrackIdForReplacement() {
        when(trackService.getTrackById("T0000001")).thenReturn(TrackDTO.builder().trackId("T0000001").build());
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1, 2, 3});

        ResponseEntity<ApiResponse<AdminUploadResponse>> response = controller.upload(
                null, "token", "admin", file, "cover", "T0000001");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        AdminUploadResponse upload = response.getBody().getData();
        assertThat(upload.getTrackId()).isEqualTo("T0000001");
        assertThat(upload.getType()).isEqualTo("cover");
        assertThat(forwardedPaths).containsExactly("/covers/T0000001.jpg");
    }

    @Test
    void uploadWithoutTrackIdGeneratesNewId() {
        when(trackService.getTrackById(any())).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "song.mp3", "audio/mpeg", new byte[]{0, 1, 2});

        ResponseEntity<ApiResponse<AdminUploadResponse>> response = controller.upload(
                null, "token", "admin", file, "audio", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        AdminUploadResponse upload = response.getBody().getData();
        assertThat(upload.getTrackId()).matches("T[0-9a-f]{8}");
        assertThat(upload.getType()).isEqualTo("audio");
        assertThat(upload.getFormat()).isEqualTo("mp3");
        assertThat(upload.getFileSize()).isEqualTo(3L);
        assertThat(upload.getUrl()).contains(upload.getTrackId());
        assertThat(forwardedPaths).hasSize(1);
        assertThat(forwardedPaths.get(0)).startsWith("/audio/").endsWith(".mp3");
    }
}
