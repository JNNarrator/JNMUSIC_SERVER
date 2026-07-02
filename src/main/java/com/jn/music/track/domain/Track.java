package com.jn.music.track.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌曲元数据，字段对齐数据库 `track` 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("track")
public class Track {

    /**
     * 全局唯一歌曲 ID，对应数据库 `track_id` 主键。
     */
    @TableId(value = "track_id", type = IdType.INPUT)
    private String trackId;

    private String name;

    private String artist;

    private String album;

    private String coverUrl;

    private Integer duration;

    private String format;

    private Long fileSize;

    private Integer trackNumber;

    private Boolean hasLyric;

    private String lyricUrl;
}
