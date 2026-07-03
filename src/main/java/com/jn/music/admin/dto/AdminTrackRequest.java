package com.jn.music.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminTrackRequest {

    private String trackId;

    @NotBlank(message = "track name is required")
    private String name;

    @NotBlank(message = "artist is required")
    private String artist;
    private String album;
    private Integer duration;
    private String format;
    private Long fileSize;
    private Integer trackNumber;
    private Boolean hasLyric;
    private String coverUrl;
    private String lyricUrl;
}
