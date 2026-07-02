package com.jn.music.track.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P0 音乐库接口定义，仅使用 GET 请求。
 */
@RequestMapping("/api/v1/tracks")
public interface TrackApi {

    /**
     * 搜索歌曲，q 必填，page/pageSize 使用文档默认值。
     */
    @GetMapping("/search")
    ApiResponse<PageResponse<TrackSummaryDTO>> searchTracks(
            @RequestParam("q") String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize);

    /**
     * 批量获取歌曲元数据，ids 为逗号分隔的 trackId 列表。
     */
    @GetMapping("/batch")
    ApiResponse<PageResponse<TrackDTO>> getTracksByIds(@RequestParam("ids") List<String> ids);

    /**
     * 获取歌曲详情。
     */
    @GetMapping("/{trackId}")
    ApiResponse<TrackDTO> getTrackById(@PathVariable("trackId") String trackId);

    /**
     * 获取播放地址，quality 可选：flac/mp3_320/mp3_128。
     */
    @GetMapping("/{trackId}/media-url")
    ApiResponse<MediaUrlDTO> getMediaUrl(
            @PathVariable("trackId") String trackId,
            @RequestParam(value = "quality", defaultValue = "flac") String quality);
}
