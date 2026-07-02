package com.jn.music.user.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.user.dto.ExistsDTO;
import com.jn.music.user.dto.TrackIdRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P1 收藏接口定义，取消收藏使用 POST 形式避免 DELETE。
 */
@RequestMapping("/api/v1/favorites")
public interface FavoriteApi {

    @GetMapping
    ApiResponse<PageResponse<TrackDTO>> listFavorites(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize);

    @PostMapping
    ApiResponse<Void> addFavorite(@RequestBody TrackIdRequest request);

    @PostMapping("/remove")
    ApiResponse<Void> removeFavorite(@RequestBody TrackIdRequest request);

    @GetMapping("/{trackId}/exists")
    ApiResponse<ExistsDTO> existsFavorite(@PathVariable("trackId") String trackId);
}
