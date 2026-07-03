package com.jn.music.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jn.music.track.service.TrackService;
import com.jn.music.user.domain.SearchHistory;
import com.jn.music.user.dto.SearchKeywordDTO;
import com.jn.music.user.mapper.SearchHistoryMapper;
import com.jn.music.user.service.SearchHistoryService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 搜索历史同步服务。
 */
@Service
public class SearchHistoryServiceImpl extends UserDataSupport implements SearchHistoryService {

    private final SearchHistoryMapper searchHistoryMapper;

    public SearchHistoryServiceImpl(TrackService trackService, SearchHistoryMapper searchHistoryMapper) {
        super(trackService);
        this.searchHistoryMapper = searchHistoryMapper;
    }

    @Override
    public List<SearchKeywordDTO> listSearchHistory(String deviceId, Integer limit) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        int normalizedLimit = normalizeLimit(limit, 20);
        return searchHistoryMapper.selectList(Wrappers.<SearchHistory>lambdaQuery()
                        .eq(SearchHistory::getDeviceId, normalizedDeviceId)
                        .orderByDesc(SearchHistory::getSearchedAt)
                        .last("LIMIT " + normalizedLimit))
                .stream()
                .map(history -> SearchKeywordDTO.builder()
                        .keyword(history.getKeyword())
                        .searchedAt(toOffsetDateTime(history.getSearchedAt()))
                        .build())
                .toList();
    }

    @Override
    public void recordKeyword(String deviceId, String keyword) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedKeyword = requireKeyword(keyword);
        SearchHistory existing = searchHistoryMapper.selectOne(Wrappers.<SearchHistory>lambdaQuery()
                .eq(SearchHistory::getDeviceId, normalizedDeviceId)
                .eq(SearchHistory::getKeyword, normalizedKeyword));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            searchHistoryMapper.insert(SearchHistory.builder()
                    .deviceId(normalizedDeviceId)
                    .keyword(normalizedKeyword)
                    .searchedAt(now)
                    .build());
            return;
        }
        existing.setSearchedAt(now);
        searchHistoryMapper.updateById(existing);
    }

    @Override
    public void clearSearchHistory(String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        searchHistoryMapper.delete(Wrappers.<SearchHistory>lambdaQuery()
                .eq(SearchHistory::getDeviceId, normalizedDeviceId));
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
