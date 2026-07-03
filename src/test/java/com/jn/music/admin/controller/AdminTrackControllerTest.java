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
import com.jn.music.common.config.FileServerProperties;
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
    private List<String> deletedPaths;

    @BeforeEach
    void setUp() {
        trackService = mock(TrackService.class);
        trackMapper = mock(TrackMapper.class);
        tokenStore = mock(AdminTokenStore.class);
        forwardedPaths = new ArrayList<>();
        deletedPaths = new ArrayList<>();
        controller = new AdminTrackController(
                trackService,
                trackMapper,
                tokenStore,
                new FileServerProperties(),
                (path, file) -> forwardedPaths.add(path),
                path -> deletedPaths.add(path));
        when(tokenStore.isValid("token", "admin")).thenReturn(true);
    }

    @Test
    void saveWithOnlyNameAndArtistGeneratesMissingMetadata() {
        when(trackMapper.insert(any(Track.class))).thenReturn(1);

        AdminTrackRequest request = new AdminTrackRequest();
        request.setName(" Song A ");
        request.setArtist(" Artist A ");

        ResponseEntity<ApiResponse<Track>> response = controller.save(null, "token", "admin", request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Track saved = response.getBody().getData();
        assertThat(saved.getTrackId()).matches("T[0-9a-f]{8}");
        assertThat(saved.getName()).isEqualTo("Song A");
        assertThat(saved.getArtist()).isEqualTo("Artist A");
        assertThat(saved.getAlbum()).isEqualTo("未知");
        assertThat(saved.getDuration()).isZero();
        assertThat(saved.getFormat()).isEqualTo("未知");
        assertThat(saved.getFileSize()).isZero();
        assertThat(saved.getTrackNumber()).isEqualTo(1);
        assertThat(saved.getHasLyric()).isFalse();
        assertThat(saved.getCoverUrl()).isNull();
        assertThat(saved.getLyricUrl()).isNull();

        ArgumentCaptor<Track> captor = ArgumentCaptor.forClass(Track.class);
        verify(trackMapper).insert(captor.capture());
        assertThat(captor.getValue().getTrackId()).isEqualTo(saved.getTrackId());
        assertThat(captor.getValue().getArtist()).isEqualTo("Artist A");
        assertThat(captor.getValue().getAlbum()).isEqualTo("未知");
        assertThat(captor.getValue().getFormat()).isEqualTo("未知");
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
        assertThat(upload.getUrl()).isEqualTo("http://jn_file.88933.vip:27472/audio/" + upload.getTrackId() + ".mp3");
        assertThat(forwardedPaths).hasSize(1);
        assertThat(forwardedPaths.get(0)).startsWith("/audio/").endsWith(".mp3");
    }

    @Test
    void deleteRemovesTrackAndKnownFiles() {
        Track track = Track.builder()
                .trackId("T0000001")
                .format("mp3")
                .coverUrl("http://jn_file.88933.vip:27472/covers/T0000001.jpg")
                .lyricUrl("/lyrics/T0000001.lrc")
                .build();
        when(trackMapper.selectById("T0000001")).thenReturn(track);
        when(trackMapper.deleteById("T0000001")).thenReturn(1);

        ResponseEntity<ApiResponse<Void>> response = controller.delete(null, "token", "admin", "T0000001");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(trackMapper).deleteById("T0000001");
        assertThat(deletedPaths).containsExactly(
                "/audio/T0000001.mp3",
                "/covers/T0000001.jpg",
                "/lyrics/T0000001.lrc");
    }
}
