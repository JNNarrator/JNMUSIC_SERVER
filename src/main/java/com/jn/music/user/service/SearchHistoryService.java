package com.jn.music.user.service;

import com.jn.music.user.dto.SearchKeywordDTO;
import java.util.List;

/**
 * 搜索历史业务接口。
 */
public interface SearchHistoryService {

    List<SearchKeywordDTO> listSearchHistory(Integer limit);

    void recordKeyword(String keyword);

    void clearSearchHistory();
}
