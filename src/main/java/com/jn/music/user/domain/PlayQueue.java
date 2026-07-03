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
 * 匿名设备播放队列条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("play_queue")
public class PlayQueue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String trackId;

    private Integer position;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
