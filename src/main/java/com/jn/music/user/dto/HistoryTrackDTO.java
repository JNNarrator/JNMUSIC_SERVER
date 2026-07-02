package com.jn.music.user.dto;

import com.jn.music.track.dto.TrackDTO;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放历史条目，包含歌曲信息和最近播放时间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryTrackDTO {

    private TrackDTO track;
    private OffsetDateTime playedAt;
}
