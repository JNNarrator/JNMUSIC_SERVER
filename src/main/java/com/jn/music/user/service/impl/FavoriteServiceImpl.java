package com.jn.music.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.service.TrackService;
import com.jn.music.user.domain.UserFavorite;
import com.jn.music.user.mapper.UserFavoriteMapper;
import com.jn.music.user.service.FavoriteService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 收藏同步服务，按匿名设备隔离数据。
 */
@Service
public class FavoriteServiceImpl extends UserDataSupport implements FavoriteService {

    private final UserFavoriteMapper favoriteMapper;

    public FavoriteServiceImpl(TrackService trackService, UserFavoriteMapper favoriteMapper) {
        super(trackService);
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    public PageResponse<TrackDTO> listFavorites(String deviceId, Integer page, Integer pageSize) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize, 50);
        Page<UserFavorite> resultPage = favoriteMapper.selectPage(new Page<>(normalizedPage, normalizedPageSize),
                Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getDeviceId, normalizedDeviceId)
                        .orderByDesc(UserFavorite::getCreatedAt));
        List<String> trackIds = resultPage.getRecords().stream().map(UserFavorite::getTrackId).toList();
        Map<String, TrackDTO> trackMap = loadTrackMap(trackIds);
        List<TrackDTO> items = trackIds.stream().map(trackMap::get).filter(Objects::nonNull).toList();
        return PageResponse.<TrackDTO>builder()
                .items(items)
                .page((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.hasNext())
                .build();
    }

    @Override
    public void addFavorite(String deviceId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedTrackId = requireTrackId(trackId);
        ensureTrackExists(normalizedTrackId);
        Long count = favoriteMapper.selectCount(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getDeviceId, normalizedDeviceId)
                .eq(UserFavorite::getTrackId, normalizedTrackId));
        if (count != null && count > 0) {
            return;
        }
        favoriteMapper.insert(UserFavorite.builder()
                .deviceId(normalizedDeviceId)
                .trackId(normalizedTrackId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public void removeFavorite(String deviceId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedTrackId = requireTrackId(trackId);
        favoriteMapper.delete(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getDeviceId, normalizedDeviceId)
                .eq(UserFavorite::getTrackId, normalizedTrackId));
    }

    @Override
    public Boolean existsFavorite(String deviceId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedTrackId = requireTrackId(trackId);
        Long count = favoriteMapper.selectCount(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getDeviceId, normalizedDeviceId)
                .eq(UserFavorite::getTrackId, normalizedTrackId));
        return count != null && count > 0;
    }
}
