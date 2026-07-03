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
 * 匿名设备播放历史，单曲只保留最近一次播放时间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("play_history")
public class PlayHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String trackId;

    private LocalDateTime playedAt;
}
