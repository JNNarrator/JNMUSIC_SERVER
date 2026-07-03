package com.jn.music.user.service;

import com.jn.music.common.PageResponse;
import com.jn.music.user.dto.HistoryTrackDTO;

/**
 * 播放历史业务接口。
 */
public interface HistoryService {

    PageResponse<HistoryTrackDTO> listHistory(String deviceId, Integer page, Integer pageSize);

    void recordPlay(String deviceId, String trackId);

    void clearHistory(String deviceId);
}
