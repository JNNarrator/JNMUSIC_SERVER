package com.jn.music.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.service.TrackService;
import com.jn.music.user.domain.PlayQueue;
import com.jn.music.user.dto.QueueItemDTO;
import com.jn.music.user.dto.QueueItemRequest;
import com.jn.music.user.mapper.PlayQueueMapper;
import com.jn.music.user.service.QueueService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 播放队列同步服务。
 */
@Service
public class QueueServiceImpl extends UserDataSupport implements QueueService {

    private final PlayQueueMapper queueMapper;

    public QueueServiceImpl(TrackService trackService, PlayQueueMapper queueMapper) {
        super(trackService);
        this.queueMapper = queueMapper;
    }

    @Override
    public List<QueueItemDTO> listQueue(String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        List<PlayQueue> queueItems = queueMapper.selectList(Wrappers.<PlayQueue>lambdaQuery()
                .eq(PlayQueue::getDeviceId, normalizedDeviceId)
                .orderByAsc(PlayQueue::getPosition));
        List<String> trackIds = queueItems.stream().map(PlayQueue::getTrackId).toList();
        Map<String, TrackDTO> trackMap = loadTrackMap(trackIds);
        return queueItems.stream()
                .map(item -> QueueItemDTO.builder()
                        .trackId(item.getTrackId())
                        .position(item.getPosition())
                        .track(trackMap.get(item.getTrackId()))
                        .build())
                .filter(item -> item.getTrack() != null)
                .toList();
    }

    @Override
    @Transactional
    public void saveQueue(String deviceId, List<QueueItemRequest> items) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        queueMapper.delete(Wrappers.<PlayQueue>lambdaQuery().eq(PlayQueue::getDeviceId, normalizedDeviceId));
        if (items == null || items.isEmpty()) {
            return;
        }
        List<QueueItemRequest> normalizedItems = items.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(item -> item.getPosition() == null ? Integer.MAX_VALUE : item.getPosition()))
                .toList();
        int position = 0;
        LocalDateTime now = LocalDateTime.now();
        for (QueueItemRequest item : normalizedItems) {
            String trackId = requireTrackId(item.getTrackId());
            ensureTrackExists(trackId);
            queueMapper.insert(PlayQueue.builder()
                    .deviceId(normalizedDeviceId)
                    .trackId(trackId)
                    .position(position++)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
    }

    @Override
    public void appendTrack(String deviceId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedTrackId = requireTrackId(trackId);
        ensureTrackExists(normalizedTrackId);
        Long count = queueMapper.selectCount(Wrappers.<PlayQueue>lambdaQuery()
                .eq(PlayQueue::getDeviceId, normalizedDeviceId)
                .eq(PlayQueue::getTrackId, normalizedTrackId));
        if (count != null && count > 0) {
            return;
        }
        Integer maxPosition = queueMapper.selectList(Wrappers.<PlayQueue>lambdaQuery()
                        .eq(PlayQueue::getDeviceId, normalizedDeviceId)
                        .orderByDesc(PlayQueue::getPosition)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(PlayQueue::getPosition)
                .orElse(-1);
        LocalDateTime now = LocalDateTime.now();
        queueMapper.insert(PlayQueue.builder()
                .deviceId(normalizedDeviceId)
                .trackId(normalizedTrackId)
                .position(maxPosition + 1)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Override
    public void removeTrack(String deviceId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedTrackId = requireTrackId(trackId);
        queueMapper.delete(Wrappers.<PlayQueue>lambdaQuery()
                .eq(PlayQueue::getDeviceId, normalizedDeviceId)
                .eq(PlayQueue::getTrackId, normalizedTrackId));
    }
}
