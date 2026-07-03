package com.jn.music.user.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.user.api.SearchHistoryApi;
import com.jn.music.user.dto.SearchKeywordDTO;
import com.jn.music.user.dto.SearchKeywordRequest;
import com.jn.music.user.service.SearchHistoryService;
import java.util.List;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 搜索历史同步接口。
 */
@RestController
public class SearchHistoryController implements SearchHistoryApi {

    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    @Override
    public ApiResponse<List<SearchKeywordDTO>> listSearchHistory(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            Integer limit) {
        return ApiResponse.success(searchHistoryService.listSearchHistory(deviceId, limit));
    }

    @Override
    public ApiResponse<Void> recordKeyword(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            SearchKeywordRequest request) {
        searchHistoryService.recordKeyword(deviceId, request != null ? request.getKeyword() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> clearSearchHistory(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        searchHistoryService.clearSearchHistory(deviceId);
        return ApiResponse.success(null);
    }
}
