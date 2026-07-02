package com.jn.music.track.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可播放媒体地址，mediaUrl 需要由文件服务支持 HTTP Range。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUrlDTO {

    private String trackId;
    private String mediaUrl;
    private String format;
    private OffsetDateTime expiresAt;
}
