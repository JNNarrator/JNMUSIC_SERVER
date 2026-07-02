package com.jn.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 布尔存在性响应，如是否已收藏。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExistsDTO {

    private Boolean exists;
}
