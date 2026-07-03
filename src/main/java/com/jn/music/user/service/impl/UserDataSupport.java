package com.jn.music.user.service.impl;

import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.service.TrackService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * P1 用户数据接口的公共边界处理。
 */
abstract class UserDataSupport {

    static final String DEFAULT_DEVICE_ID = "anonymous";

    private final TrackService trackService;

    UserDataSupport(TrackService trackService) {
        this.trackService = trackService;
    }

    String normalizeDeviceId(String deviceId) {
        return StringUtils.hasText(deviceId) ? deviceId.trim() : DEFAULT_DEVICE_ID;
    }

    String requireTrackId(String trackId) {
        if (!StringUtils.hasText(trackId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "trackId 不能为空");
        }
        return trackId.trim();
    }

    String requireKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "keyword 不能为空");
        }
        return keyword.trim();
    }

    int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    int normalizePageSize(Integer pageSize, int defaultPageSize) {
        int normalizedPageSize = pageSize == null || pageSize < 1 ? defaultPageSize : pageSize;
        return Math.min(normalizedPageSize, 50);
    }

    int normalizeLimit(Integer limit, int defaultLimit) {
        int normalizedLimit = limit == null || limit < 1 ? defaultLimit : limit;
        return Math.min(normalizedLimit, 50);
    }

    void ensureTrackExists(String trackId) {
        trackService.getTrackById(trackId);
    }

    Map<String, TrackDTO> loadTrackMap(List<String> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return Collections.emptyMap();
        }
        PageResponse<TrackDTO> page = trackService.getTracksByIds(trackIds);
        Map<String, TrackDTO> trackMap = new LinkedHashMap<>();
        if (page.getItems() != null) {
            for (TrackDTO track : page.getItems()) {
                trackMap.put(track.getTrackId(), track);
            }
        }
        return trackMap;
    }
}
