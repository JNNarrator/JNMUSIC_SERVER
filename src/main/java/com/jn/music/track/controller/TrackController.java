package com.jn.music.track.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.track.dto.TrackBatchRequest;
import com.jn.music.track.service.TrackCacheService;
import com.jn.music.track.service.TrackService;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.dto.TrackWithUrlDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "音乐库", description = "音乐库相关接口")
@RestController
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackService trackService;
    private final TrackCacheService cacheService;

    public TrackController(TrackService trackService, TrackCacheService cacheService) {
        this.trackService = trackService;
        this.cacheService = cacheService;
    }

    @Operation(summary = "搜索歌曲", description = "根据关键词搜索歌曲")
    @GetMapping("/search")
    public ApiResponse<PageResponse<TrackSummaryDTO>> searchTracks(
            @Parameter(description = "搜索关键词") @RequestParam("q") String q,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        String keyword = trimToEmpty(q);
        if (keyword.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        PageParams pageParams = normalizePageParams(page, pageSize, 20);
        return ApiResponse.success(trackService.searchTracks(keyword, pageParams.page(), pageParams.pageSize()));
    }

    @Operation(summary = "获取歌曲列表", description = "分页获取歌曲列表")
    @GetMapping
    public ApiResponse<PageResponse<TrackSummaryDTO>> listTracks(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @Parameter(description = "是否刷新缓存") @RequestParam(value = "refresh", defaultValue = "false") Boolean refresh) {
        PageParams pageParams = normalizePageParams(page, pageSize, 20);
        return ApiResponse.success(trackService.listTracks(pageParams.page(), pageParams.pageSize(), refresh));
    }

    @Operation(summary = "批量获取歌曲", description = "根据ID批量获取歌曲信息")
    @GetMapping("/batch")
    public ApiResponse<PageResponse<TrackDTO>> getTracksByIds(
            @Parameter(description = "歌曲ID列表，逗号分隔") @RequestParam("ids") List<String> ids) {
        List<String> normalizedIds = normalizeTrackIds(ids);
        if (normalizedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ids 不能为空");
        }
        if (normalizedIds.size() > TrackBatchRequest.MAX_TRACK_IDS) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ids 最多支持 50 个 trackId");
        }
        return ApiResponse.success(trackService.getTracksByIds(normalizedIds));
    }

    @Operation(summary = "获取歌曲详情", description = "根据ID获取歌曲详情")
    @GetMapping("/{trackId}")
    public ApiResponse<TrackDTO> getTrackById(
            @Parameter(description = "歌曲ID") @PathVariable("trackId") String trackId) {
        return ApiResponse.success(trackService.getTrackById(trackId));
    }

    @Operation(summary = "获取播放链接", description = "获取歌曲的播放直链")
    @GetMapping("/{trackId}/media-url")
    public ApiResponse<MediaUrlDTO> getMediaUrl(
            @Parameter(description = "歌曲ID") @PathVariable("trackId") String trackId) {
        return ApiResponse.success(trackService.getMediaUrl(trackId));
    }

    @Operation(summary = "批量获取播放链接", description = "批量获取歌曲的播放直链")
    @GetMapping("/media-urls")
    public ApiResponse<Map<String, MediaUrlDTO>> getMediaUrls(
            @Parameter(description = "歌曲ID列表，逗号分隔") @RequestParam("ids") List<String> ids) {
        List<String> normalizedIds = normalizeTrackIds(ids);
        if (normalizedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ids 不能为空");
        }
        if (normalizedIds.size() > TrackBatchRequest.MAX_TRACK_IDS) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ids 最多支持 50 个 trackId");
        }
        return ApiResponse.success(trackService.getMediaUrls(normalizedIds));
    }

    @Operation(summary = "获取歌词", description = "获取歌曲的歌词内容")
    @GetMapping("/{trackId}/lyrics")
    public ApiResponse<String> getLyrics(
            @Parameter(description = "歌曲ID") @PathVariable("trackId") String trackId) {
        return ApiResponse.success(trackService.getLyrics(trackId));
    }

    @Operation(summary = "获取歌曲列表(APP)", description = "返回带播放直链的歌曲列表，适用于APP直接播放")
    @GetMapping("/app")
    public ApiResponse<PageResponse<TrackWithUrlDTO>> listTracksForApp(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @Parameter(description = "是否刷新缓存") @RequestParam(value = "refresh", defaultValue = "false") Boolean refresh) {
        PageParams pageParams = normalizePageParams(page, pageSize, 20);
        return ApiResponse.success(trackService.listTracksWithUrl(pageParams.page(), pageParams.pageSize(), refresh));
    }

    @Operation(summary = "搜索歌曲(APP)", description = "搜索带播放直链的歌曲，适用于APP直接播放")
    @GetMapping("/app/search")
    public ApiResponse<PageResponse<TrackWithUrlDTO>> searchTracksForApp(
            @Parameter(description = "搜索关键词") @RequestParam("q") String q,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        String keyword = trimToEmpty(q);
        if (keyword.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        PageParams pageParams = normalizePageParams(page, pageSize, 20);
        return ApiResponse.success(trackService.searchTracksWithUrl(keyword, pageParams.page(), pageParams.pageSize()));
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

    @Operation(summary = "缓存状态", description = "查询歌曲直链缓存的刷新进度")
    @GetMapping("/cache/status")
    public ApiResponse<Map<String, Object>> cacheStatus() {
        return ApiResponse.success(cacheService.getStatus());
    }

    private record PageParams(int page, int pageSize) {
    }
}
