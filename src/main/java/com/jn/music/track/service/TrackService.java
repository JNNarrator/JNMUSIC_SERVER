package com.jn.music.track.service;

import com.jn.music.common.PageResponse;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.dto.TrackWithUrlDTO;
import java.util.List;

public interface TrackService {

    PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize);

    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize);

    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize, boolean refresh);

    TrackDTO getTrackById(String trackId);

    PageResponse<TrackDTO> getTracksByIds(List<String> ids);

    MediaUrlDTO getMediaUrl(String trackId);

    String getLyrics(String trackId);

    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize);

    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize, boolean refresh);

    PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize);
}
