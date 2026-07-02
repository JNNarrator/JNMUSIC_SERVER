package com.jn.music.track.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量歌曲元数据请求，文档约定最多 50 个 trackId。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackBatchRequest {

    public static final int MAX_TRACK_IDS = 50;

    private List<String> ids;
}
