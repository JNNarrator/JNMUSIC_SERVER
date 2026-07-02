package com.jn.music.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminTrackRequest {

    private String trackId;

    @NotBlank(message = "track name is required")
    private String name;
    private String artist;
    private String album;
    @NotNull(message = "duration is required")
    private Integer duration;
    private String format;
    private Long fileSize;
    private Integer trackNumber;
    private Boolean hasLyric;
    private String coverUrl;
    private String lyricUrl;
}
