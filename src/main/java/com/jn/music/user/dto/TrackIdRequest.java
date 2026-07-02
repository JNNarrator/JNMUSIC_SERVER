package com.jn.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单首歌曲操作请求，如收藏、记录播放、追加队列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackIdRequest {

    private String trackId;
}
