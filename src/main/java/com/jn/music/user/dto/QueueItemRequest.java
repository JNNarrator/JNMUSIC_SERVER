package com.jn.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存队列时的轻量条目，避免客户端重复提交完整歌曲元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueItemRequest {

    private String trackId;
    private Integer position;
}
