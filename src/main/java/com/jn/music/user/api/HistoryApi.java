package com.jn.music.user.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.PageResponse;
import com.jn.music.user.dto.HistoryTrackDTO;
import com.jn.music.user.dto.TrackIdRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P1 播放历史接口定义，清空历史使用 POST 形式避免 DELETE。
 */
@RequestMapping("/api/v1/history")
public interface HistoryApi {

    @GetMapping
    ApiResponse<PageResponse<HistoryTrackDTO>> listHistory(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize);

    @PostMapping
    ApiResponse<Void> recordPlay(@RequestBody TrackIdRequest request);

    @PostMapping("/clear")
    ApiResponse<Void> clearHistory();
}
