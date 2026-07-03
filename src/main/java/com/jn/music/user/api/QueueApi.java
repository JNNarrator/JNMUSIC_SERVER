package com.jn.music.user.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.user.dto.QueueItemDTO;
import com.jn.music.user.dto.SaveQueueRequest;
import com.jn.music.user.dto.TrackIdRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * P1 播放队列接口定义。
 */
@RequestMapping("/api/v1/queue")
public interface QueueApi {

    @GetMapping
    ApiResponse<List<QueueItemDTO>> listQueue(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId);

    @PutMapping
    ApiResponse<Void> saveQueue(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestBody SaveQueueRequest request);

    @PostMapping("/items")
    ApiResponse<Void> appendTrack(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestBody TrackIdRequest request);

    @DeleteMapping("/items/{trackId}")
    ApiResponse<Void> removeTrack(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable("trackId") String trackId);
}
