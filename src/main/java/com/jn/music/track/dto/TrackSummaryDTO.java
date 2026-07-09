package com.jn.music.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索和列表场景使用的歌曲摘要信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackSummaryDTO {

    private String trackId;
    private String name;
    private String artist;
    private String album;
    private String coverUrl;
    private Integer duration;
    private String format;
    private Long fileSize;
}
