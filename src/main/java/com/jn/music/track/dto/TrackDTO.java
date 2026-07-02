package com.jn.music.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌曲完整元数据，所有接口统一使用 trackId 标识歌曲。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackDTO {

    private String trackId;
    private String name;
    private String artist;
    private String album;
    private String coverUrl;
    private Integer duration;
    private String format;
    private Long fileSize;
    private Integer trackNumber;
    private Boolean hasLyric;
    private String lyricUrl;
}
