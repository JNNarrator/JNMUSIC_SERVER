package com.jn.music.track.service;

import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.dto.TrackWithUrlDTO;
import java.util.List;
import java.util.Map;

public interface TrackService {

    PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize);

    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize);

    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize, boolean refresh);

    TrackDTO getTrackById(String trackId);

    PageResponse<TrackDTO> getTracksByIds(List<String> ids);

    MediaUrlDTO getMediaUrl(String trackId);

    /**
     * 批量获取播放直链
     * @param trackIds trackId列表
     * @return trackId -> MediaUrlDTO 的映射
     */
    Map<String, MediaUrlDTO> getMediaUrls(List<String> trackIds);

    String getLyrics(String trackId);

    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize);

    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize, boolean refresh);

    PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize);
}
