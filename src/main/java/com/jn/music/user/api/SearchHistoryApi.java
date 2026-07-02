package com.jn.music.user.api;

import com.jn.music.common.ApiResponse;
import com.jn.music.user.dto.SearchKeywordDTO;
import com.jn.music.user.dto.SearchKeywordRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P1 搜索历史接口定义，清空历史使用 POST 形式避免 DELETE。
 */
@RequestMapping("/api/v1/search-history")
public interface SearchHistoryApi {

    @GetMapping
    ApiResponse<List<SearchKeywordDTO>> listSearchHistory(
            @RequestParam(value = "limit", defaultValue = "20") Integer limit);

    @PostMapping
    ApiResponse<Void> recordKeyword(@RequestBody SearchKeywordRequest request);

    @PostMapping("/clear")
    ApiResponse<Void> clearSearchHistory();
}
