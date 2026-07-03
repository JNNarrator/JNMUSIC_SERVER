package com.jn.music.user.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.user.dto.ExistsDTO;
import com.jn.music.user.dto.TrackIdRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P1 收藏接口定义。
 */
@RequestMapping("/api/v1/favorites")
public interface FavoriteApi {

    @GetMapping
    ApiResponse<PageResponse<TrackDTO>> listFavorites(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize);

    @PostMapping
    ApiResponse<Void> addFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestBody TrackIdRequest request);

    @DeleteMapping("/{trackId}")
    ApiResponse<Void> removeFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable("trackId") String trackId);

    @GetMapping("/{trackId}/exists")
    ApiResponse<ExistsDTO> existsFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable("trackId") String trackId);
}
