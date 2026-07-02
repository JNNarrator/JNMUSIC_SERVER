package com.jn.music.user.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 覆盖保存播放队列请求，对应文档 PUT /queue 的 POST 化版本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveQueueRequest {

    private List<QueueItemRequest> items;
}
