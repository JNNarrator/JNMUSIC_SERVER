package com.jn.music.user.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.user.api.QueueApi;
import com.jn.music.user.dto.QueueItemDTO;
import com.jn.music.user.dto.SaveQueueRequest;
import com.jn.music.user.dto.TrackIdRequest;
import com.jn.music.user.service.QueueService;
import java.util.List;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 播放队列同步接口。
 */
@RestController
public class QueueController implements QueueApi {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public ApiResponse<List<QueueItemDTO>> listQueue(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return ApiResponse.success(queueService.listQueue(deviceId));
    }

    @Override
    public ApiResponse<Void> saveQueue(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            SaveQueueRequest request) {
        queueService.saveQueue(deviceId, request != null ? request.getItems() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> appendTrack(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            TrackIdRequest request) {
        queueService.appendTrack(deviceId, request != null ? request.getTrackId() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> removeTrack(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            String trackId) {
        queueService.removeTrack(deviceId, trackId);
        return ApiResponse.success(null);
    }
}
