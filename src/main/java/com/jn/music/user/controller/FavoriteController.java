package com.jn.music.user.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.user.api.FavoriteApi;
import com.jn.music.user.dto.ExistsDTO;
import com.jn.music.user.dto.TrackIdRequest;
import com.jn.music.user.service.FavoriteService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 收藏同步接口。
 */
@RestController
public class FavoriteController implements FavoriteApi {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Override
    public ApiResponse<PageResponse<TrackDTO>> listFavorites(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            Integer page,
            Integer pageSize) {
        return ApiResponse.success(favoriteService.listFavorites(deviceId, page, pageSize));
    }

    @Override
    public ApiResponse<Void> addFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            TrackIdRequest request) {
        favoriteService.addFavorite(deviceId, request != null ? request.getTrackId() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> removeFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            String trackId) {
        favoriteService.removeFavorite(deviceId, trackId);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<ExistsDTO> existsFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            String trackId) {
        return ApiResponse.success(ExistsDTO.builder()
                .exists(favoriteService.existsFavorite(deviceId, trackId))
                .build());
    }
}
