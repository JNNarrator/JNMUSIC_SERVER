package com.jn.music.track.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("lyrics_cache")
public class LyricsCache {

    @TableId(value = "track_id", type = IdType.INPUT)
    private String trackId;

    private String lyrics;

    private LocalDateTime updatedAt;
}
