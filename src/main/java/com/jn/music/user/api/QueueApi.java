package com.jn.music.user.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.user.dto.QueueItemDTO;
import com.jn.music.user.dto.SaveQueueRequest;
import com.jn.music.user.dto.TrackIdRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * P1 播放队列接口定义，覆盖保存和移除都使用 POST 形式。
 */
@RequestMapping("/api/v1/queue")
public interface QueueApi {

    @GetMapping
    ApiResponse<List<QueueItemDTO>> listQueue();

    @PostMapping("/save")
    ApiResponse<Void> saveQueue(@RequestBody SaveQueueRequest request);

    @PostMapping("/items")
    ApiResponse<Void> appendTrack(@RequestBody TrackIdRequest request);

    @PostMapping("/items/remove")
    ApiResponse<Void> removeTrack(@RequestBody TrackIdRequest request);
}
