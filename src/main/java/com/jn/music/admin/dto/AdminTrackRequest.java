package com.jn.music.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminTrackRequest {

    @NotBlank(message = "歌曲ID不能为空")
    private String trackId;

    @NotBlank(message = "歌曲名不能为空")
    private String name;

    @NotBlank(message = "歌手名不能为空")
    private String artist;

    private String album;

    @NotNull(message = "时长不能为空")
    private Integer duration;

    private String format;

    private Long fileSize;

    private Integer trackNumber;

    private Boolean hasLyric;

    private String coverUrl;

    private String lyricUrl;
}
