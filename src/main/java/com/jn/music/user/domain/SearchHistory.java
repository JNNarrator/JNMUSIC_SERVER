package com.jn.music.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匿名设备搜索历史，关键词按最近搜索时间排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("search_history")
public class SearchHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String keyword;

    private LocalDateTime searchedAt;
}
