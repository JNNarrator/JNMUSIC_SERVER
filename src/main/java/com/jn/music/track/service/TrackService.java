package com.jn.music.track.service;

import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.dto.TrackWithUrlDTO;
import java.util.List;

/**
 * 音乐库业务接口，只定义能力，不在当前阶段实现具体逻辑。
 */
public interface TrackService {

    /**
     * 搜索歌曲，page 从 1 开始，pageSize 最大值由实现层校验。
     */
    PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize);

    /**
     * 歌曲分页列表，page 从 1 开始。
     */
    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize);

    /**
     * 根据 trackId 获取完整歌曲元数据。
     */
    TrackDTO getTrackById(String trackId);

    /**
     * 批量获取歌曲元数据，ids 最多 50 个。
     */
    PageResponse<TrackDTO> getTracksByIds(List<String> ids);

    /**
     * 获取可播放地址
     */
    MediaUrlDTO getMediaUrl(String trackId);

    /**
     * 获取歌词内容（LRC 格式文本）。
     */
    String getLyrics(String trackId);

    /**
     * 获取带播放直链的歌曲列表，适用于APP直接播放。
     */
    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize);

    /**
     * 搜索带播放直链的歌曲，适用于APP直接播放。
     */
    PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize);
}
