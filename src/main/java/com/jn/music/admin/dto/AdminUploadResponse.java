package com.jn.music.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUploadResponse {

    private String trackId;
    private String type;
    private String fileName;
    private String format;
    private Long fileSize;
    private String url;
}
