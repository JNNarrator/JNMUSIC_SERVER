package com.jn.music.user.dto;

import com.jn.music.track.dto.TrackDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放队列条目，position 用于维护客户端播放顺序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueItemDTO {

    private String trackId;
    private Integer position;
    private TrackDTO track;
}
