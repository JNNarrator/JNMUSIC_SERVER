package com.jn.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索历史关键词记录请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchKeywordRequest {

    private String keyword;
}
