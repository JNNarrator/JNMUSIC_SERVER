package com.jn.music.user.service;

import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackDTO;

/**
 * 收藏业务接口，预留匿名设备态和登录态同步能力。
 */
public interface FavoriteService {

    PageResponse<TrackDTO> listFavorites(String deviceId, Integer page, Integer pageSize);

    void addFavorite(String deviceId, String trackId);

    void removeFavorite(String deviceId, String trackId);

    Boolean existsFavorite(String deviceId, String trackId);
}
