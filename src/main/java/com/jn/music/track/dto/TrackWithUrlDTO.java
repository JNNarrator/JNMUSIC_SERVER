package com.jn.music.track.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 包含播放直链的歌曲信息，适用于APP直接播放场景。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackWithUrlDTO {

    private String trackId;
    private String name;
    private String artist;
    private String format;
    private Long fileSize;
    private String mediaUrl;
    private OffsetDateTime urlExpiresAt;
}
