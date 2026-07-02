package com.jn.music.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页响应结构，items 保存当前页数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;

    private Integer page;
    private Integer pageSize;
    private Long total;
    private Boolean hasMore;
}
