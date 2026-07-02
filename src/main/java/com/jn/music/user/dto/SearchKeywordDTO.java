package com.jn.music.user.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索历史关键词，保留记录时间便于排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchKeywordDTO {

    private String keyword;
    private OffsetDateTime searchedAt;
}
