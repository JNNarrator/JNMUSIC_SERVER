package com.jn.music.track.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.track.dto.TrackBatchRequest;
import com.jn.music.track.service.TrackService;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam("q") String q,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        String keyword = trimToEmpty(q);
        if (keyword.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        PageParams pageParams = normalizePageParams(page, pageSize, 20);
        return ApiResponse.success(trackService.searchTracks(keyword, pageParams.page(), pageParams.pageSize()));
    }

    @GetMapping
    public ApiResponse<PageResponse<TrackSummaryDTO>> listTracks(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        PageParams pageParams = normalizePageParams(page, pageSize, 20);
        return ApiResponse.success(trackService.listTracks(pageParams.page(), pageParams.pageSize()));
    }

    @GetMapping("/batch")
    public ApiResponse<PageResponse<TrackDTO>> getTracksByIds(@RequestParam("ids") List<String> ids) {
        List<String> normalizedIds = normalizeTrackIds(ids);
        if (normalizedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ids 不能为空");
        }
        if (normalizedIds.size() > TrackBatchRequest.MAX_TRACK_IDS) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ids 最多支持 50 个 trackId");
        }
        return ApiResponse.success(trackService.getTracksByIds(normalizedIds));
    }

    @GetMapping("/{trackId}")
    public ApiResponse<TrackDTO> getTrackById(@PathVariable("trackId") String trackId) {
        return ApiResponse.success(trackService.getTrackById(trackId));
    }

    @GetMapping("/{trackId}/media-url")
    public ApiResponse<MediaUrlDTO> getMediaUrl(
            @PathVariable("trackId") String trackId) {
        return ApiResponse.success(trackService.getMediaUrl(trackId));
    }

    private PageParams normalizePageParams(Integer page, Integer pageSize, int defaultPageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? defaultPageSize : pageSize;
        if (normalizedPageSize > 50) {
            normalizedPageSize = 50;
        }
        return new PageParams(normalizedPage, normalizedPageSize);
    }

    private List<String> normalizeTrackIds(List<String> ids) {
        Set<String> uniqueIds = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                String[] parts = trimToEmpty(id).split(",");
                for (String part : parts) {
                    String normalizedId = trimToEmpty(part);
                    if (!normalizedId.isEmpty()) {
                        uniqueIds.add(normalizedId);
                    }
                }
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record PageParams(int page, int pageSize) {
    }
}
