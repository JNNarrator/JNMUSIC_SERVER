package com.jn.music.track.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackSearchRequest;
import com.jn.music.track.service.TrackService;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P0 音乐库接口实现。
 */
@RestController
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<TrackSummaryDTO>> searchTracks(
            @RequestBody(required = false) TrackSearchRequest searchRequest,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        String keyword = "";
        if (searchRequest != null && searchRequest.getQ() != null) {
            keyword = searchRequest.getQ().trim();
        }

        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 50) {
            pageSize = 50;
        }
        return ApiResponse.success(trackService.searchTracks(keyword, page, pageSize));
    }

    @GetMapping
    public ApiResponse<PageResponse<TrackSummaryDTO>> listTracks(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 50) {
            pageSize = 50;
        }
        return ApiResponse.success(trackService.listTracks(page, pageSize));
    }

    @GetMapping("/batch")
    public ApiResponse<PageResponse<TrackDTO>> getTracksByIds(@RequestParam("ids") List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.success(PageResponse.<TrackDTO>builder().build());
        }
        return ApiResponse.success(trackService.getTracksByIds(ids));
    }

    @GetMapping("/{trackId}")
    public ApiResponse<TrackDTO> getTrackById(@PathVariable("trackId") String trackId) {
        return ApiResponse.success(trackService.getTrackById(trackId));
    }

    @GetMapping("/{trackId}/media-url")
    public ApiResponse<MediaUrlDTO> getMediaUrl(
            @PathVariable("trackId") String trackId,
            @RequestParam(value = "quality", defaultValue = "flac") String quality) {
        return ApiResponse.success(trackService.getMediaUrl(trackId, quality));
    }
}
