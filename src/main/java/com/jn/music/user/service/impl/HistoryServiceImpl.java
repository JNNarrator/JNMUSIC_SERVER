package com.jn.music.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.service.TrackService;
import com.jn.music.user.domain.PlayHistory;
import com.jn.music.user.dto.HistoryTrackDTO;
import com.jn.music.user.mapper.PlayHistoryMapper;
import com.jn.music.user.service.HistoryService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 播放历史同步服务。
 */
@Service
public class HistoryServiceImpl extends UserDataSupport implements HistoryService {

    private final PlayHistoryMapper historyMapper;

    public HistoryServiceImpl(TrackService trackService, PlayHistoryMapper historyMapper) {
        super(trackService);
        this.historyMapper = historyMapper;
    }

    @Override
    public PageResponse<HistoryTrackDTO> listHistory(String deviceId, Integer page, Integer pageSize) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize, 50);
        Page<PlayHistory> resultPage = historyMapper.selectPage(new Page<>(normalizedPage, normalizedPageSize),
                Wrappers.<PlayHistory>lambdaQuery()
                        .eq(PlayHistory::getDeviceId, normalizedDeviceId)
                        .orderByDesc(PlayHistory::getPlayedAt));
        List<String> trackIds = resultPage.getRecords().stream().map(PlayHistory::getTrackId).toList();
        Map<String, TrackDTO> trackMap = loadTrackMap(trackIds);
        List<HistoryTrackDTO> items = resultPage.getRecords().stream()
                .filter(history -> trackMap.containsKey(history.getTrackId()))
                .map(history -> HistoryTrackDTO.builder()
                        .track(trackMap.get(history.getTrackId()))
                        .playedAt(toOffsetDateTime(history.getPlayedAt()))
                        .build())
                .toList();
        return PageResponse.<HistoryTrackDTO>builder()
                .items(items)
                .page((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.hasNext())
                .build();
    }

    @Override
    public void recordPlay(String deviceId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedTrackId = requireTrackId(trackId);
        ensureTrackExists(normalizedTrackId);
        PlayHistory existing = historyMapper.selectOne(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getDeviceId, normalizedDeviceId)
                .eq(PlayHistory::getTrackId, normalizedTrackId));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            historyMapper.insert(PlayHistory.builder()
                    .deviceId(normalizedDeviceId)
                    .trackId(normalizedTrackId)
                    .playedAt(now)
                    .build());
            return;
        }
        existing.setPlayedAt(now);
        historyMapper.updateById(existing);
    }

    @Override
    public void clearHistory(String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        historyMapper.delete(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getDeviceId, normalizedDeviceId));
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
