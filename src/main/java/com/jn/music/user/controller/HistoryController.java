package com.jn.music.user.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.user.api.HistoryApi;
import com.jn.music.user.dto.HistoryTrackDTO;
import com.jn.music.user.dto.TrackIdRequest;
import com.jn.music.user.service.HistoryService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 播放历史同步接口。
 */
@RestController
public class HistoryController implements HistoryApi {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Override
    public ApiResponse<PageResponse<HistoryTrackDTO>> listHistory(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            Integer page,
            Integer pageSize) {
        return ApiResponse.success(historyService.listHistory(deviceId, page, pageSize));
    }

    @Override
    public ApiResponse<Void> recordPlay(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            TrackIdRequest request) {
        historyService.recordPlay(deviceId, request != null ? request.getTrackId() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> clearHistory(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        historyService.clearHistory(deviceId);
        return ApiResponse.success(null);
    }
}
