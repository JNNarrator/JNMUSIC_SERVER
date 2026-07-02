package com.jn.music.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌曲搜索查询参数，page 从 1 开始，pageSize 最大 50。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackSearchRequest {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private String q;

    @Builder.Default
    private Integer page = DEFAULT_PAGE;

    @Builder.Default
    private Integer pageSize = DEFAULT_PAGE_SIZE;
}
