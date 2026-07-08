package com.jn.music.track.service;

import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.dto.LanzouDirectLink;
import com.jn.music.lanzou.dto.LanzouFile;
import com.jn.music.lanzou.dto.LanzouFolder;
import com.jn.music.lanzou.dto.LanzouPageResult;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.service.impl.TrackServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TrackServiceImpl 从蓝奏云根目录读取音频的行为契约。
 * 用户目标：Service 用 LanzouApiClient.listFiles("-1", page) 拉列表；只保留音频后缀；
 * 文件名 "作者 - 歌名.扩展名" 惯例解析；getMediaUrl 返回 getFileDownloadLink 直链。
 */
class LanzouTrackServiceTest {

    private LanzouApiClient lanzouClient;
    private TrackServiceImpl service;

    @BeforeEach
    void setUp() {
        lanzouClient = mock(LanzouApiClient.class);
        service = new TrackServiceImpl(lanzouClient);
    }

    @Test
    void listTracks_shouldMapAudioFilesFromLanzouRoot() {
        LanzouPageResult page1 = new LanzouPageResult(1, 20, List.of(
                new LanzouFile("295475567", "星火社 - 游京.flac", 12345L, ""),
                new LanzouFile("295475568", "周杰伦 - 晴天.mp3", 5000L, ""),
                new LanzouFile("295475569", "无关文档.pdf", 1000L, "")
        ), List.<LanzouFolder>of());
        when(lanzouClient.listFiles(eq("-1"), eq(1))).thenReturn(page1);

        PageResponse<TrackSummaryDTO> result = service.listTracks(1, 20);

        assertEquals(2, result.getItems().size(), "应过滤非音频文件");
        TrackSummaryDTO first = result.getItems().get(0);
        assertEquals("295475567", first.getTrackId());
        assertEquals("游京", first.getName());
        assertEquals("星火社", first.getArtist());
        TrackSummaryDTO second = result.getItems().get(1);
        assertEquals("晴天", second.getName());
        assertEquals("周杰伦", second.getArtist());
        assertEquals(1, result.getPage());
        assertEquals(2L, result.getTotal());
    }

    @Test
    void listTracks_shouldTolerateFileNameWithoutDash() {
        LanzouPageResult p = new LanzouPageResult(1, 20, List.of(
                new LanzouFile("1", "some_song.mp3", 1L, "")
        ), List.of());
        when(lanzouClient.listFiles(eq("-1"), eq(1))).thenReturn(p);

        PageResponse<TrackSummaryDTO> result = service.listTracks(1, 20);
        assertEquals(1, result.getItems().size());
        TrackSummaryDTO only = result.getItems().get(0);
        assertEquals("some_song", only.getName());
        assertNull(only.getArtist(), "无 dash 时 artist 应为空");
    }

    @Test
    void searchTracks_shouldFilterByKeywordAgainstRootList() {
        LanzouPageResult p = new LanzouPageResult(1, 20, List.of(
                new LanzouFile("1", "周杰伦 - 晴天.mp3", 1L, ""),
                new LanzouFile("2", "星火社 - 游京.flac", 1L, ""),
                new LanzouFile("3", "陈奕迅 - 十年.flac", 1L, "")
        ), List.of());
        when(lanzouClient.listFiles(eq("-1"), eq(1))).thenReturn(p);

        PageResponse<TrackSummaryDTO> result = service.searchTracks("陈奕迅", 1, 20);
        assertEquals(1, result.getItems().size());
        assertEquals("十年", result.getItems().get(0).getName());
    }

    @Test
    void getMediaUrl_shouldReturnLanzouDirectLink() {
        LanzouPageResult p = new LanzouPageResult(1, 20, List.of(
                new LanzouFile("295475567", "星火社 - 游京.flac", 12345L, "")
        ), List.of());
        when(lanzouClient.listFiles(eq("-1"), eq(1))).thenReturn(p);

        Instant expires = Instant.now().plus(Duration.ofHours(4));
        when(lanzouClient.getFileDownloadLink("295475567"))
                .thenReturn(new LanzouDirectLink("https://pdf2.example/audio.flac?sg=x", expires));

        MediaUrlDTO url = service.getMediaUrl("295475567");
        assertEquals("295475567", url.getTrackId());
        assertEquals("https://pdf2.example/audio.flac?sg=x", url.getMediaUrl());
        assertEquals("flac", url.getFormat());
        assertNotNull(url.getExpiresAt());
    }

    @Test
    void getMediaUrl_shouldRejectBlank() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMediaUrl("  "));
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
    }

    @Test
    void getTrackById_shouldReturnDtoFromCache() {
        LanzouPageResult p = new LanzouPageResult(1, 20, List.of(
                new LanzouFile("1001", "周杰伦 - 晴天.mp3", 1L, "")
        ), List.of());
        when(lanzouClient.listFiles(eq("-1"), eq(1))).thenReturn(p);

        TrackDTO dto = service.getTrackById("1001");
        assertEquals("1001", dto.getTrackId());
        assertEquals("晴天", dto.getName());
        assertEquals("周杰伦", dto.getArtist());
        assertEquals("mp3", dto.getFormat());
    }
}
