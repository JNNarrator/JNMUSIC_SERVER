package com.jn.music.track.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jn.music.common.PageResponse;
import com.jn.music.track.domain.Track;
import com.jn.music.track.dto.MediaQuality;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.mapper.TrackMapper;
import com.jn.music.track.service.TrackService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * P0 音乐库服务实现，当前仅覆盖元数据读取与播放地址拼接。
 */
@Service
public class TrackServiceImpl extends ServiceImpl<TrackMapper, Track> implements TrackService {

    @Override
    public PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize) {
        // 中文 LIKE 模糊匹配；当前版本未接入拼音列。
        String likePattern = "%" + trimToEmpty(keyword) + "%";
        Page<Track> pageable = new Page<>(page, pageSize);
        Page<Track> resultPage = lambdaQuery()
                .like(Track::getName, likePattern)
                .or()
                .like(Track::getArtist, likePattern)
                .or()
                .like(Track::getAlbum, likePattern)
                .orderByDesc(Track::getTrackId)
                .page(pageable);

        List<TrackSummaryDTO> items = resultPage.getRecords()
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return PageResponse.<TrackSummaryDTO>builder()
                .items(items)
                .page((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.hasNext())
                .build();
    }

    @Override
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize) {
        Page<Track> pageable = new Page<>(page, pageSize);
        Page<Track> resultPage = lambdaQuery()
                .orderByDesc(Track::getTrackId)
                .page(pageable);

        List<TrackSummaryDTO> items = resultPage.getRecords()
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return PageResponse.<TrackSummaryDTO>builder()
                .items(items)
                .page((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.hasNext())
                .build();
    }

    @Override
    public TrackDTO getTrackById(String trackId) {
        Track track = lambdaQuery().eq(Track::getTrackId, trackId).one();
        return toDto(track);
    }

    @Override
    public PageResponse<TrackDTO> getTracksByIds(List<String> ids) {
        List<Track> tracks = lambdaQuery().in(Track::getTrackId, ids).list();
        List<TrackDTO> items = tracks.stream().map(this::toDto).collect(Collectors.toList());
        return PageResponse.<TrackDTO>builder()
                .items(items)
                .page(1)
                .pageSize(ids.size())
                .total((long) items.size())
                .hasMore(false)
                .build();
    }

    @Override
    public MediaUrlDTO getMediaUrl(String trackId, String quality) {
        Track track = lambdaQuery().select(Track::getFormat, Track::getHasLyric).eq(Track::getTrackId, trackId).one();
        if (track == null) {
            return null;
        }

        String extension = track.getFormat();
        MediaUrlDTO.MediaUrlDTOBuilder builder = MediaUrlDTO.builder()
                .trackId(trackId)
                .mediaUrl("http://jn_file.88933.vip:27472/audio/" + trackId + "." + extension)
                .format(extension)
                .expiresAt(OffsetDateTime.now().plusHours(24).withOffsetSameInstant(ZoneOffset.UTC));

        if (MediaQuality.MP3_320.getCode().equalsIgnoreCase(quality)) {
            builder.format("mp3_320");
            builder.mediaUrl("http://jn_file.88933.vip:27472/audio/" + trackId + ".mp3_320");
        } else if (MediaQuality.MP3_128.getCode().equalsIgnoreCase(quality)) {
            builder.format("mp3_128");
            builder.mediaUrl("http://jn_file.88933.vip:27472/audio/" + trackId + ".mp3_128");
        }

        return builder.build();
    }

    private TrackSummaryDTO toSummary(Track track) {
        if (track == null) {
            return null;
        }
        return TrackSummaryDTO.builder()
                .trackId(track.getTrackId())
                .name(track.getName())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .coverUrl(track.getCoverUrl())
                .duration(track.getDuration())
                .build();
    }

    private TrackDTO toDto(Track track) {
        if (track == null) {
            return null;
        }
        return TrackDTO.builder()
                .trackId(track.getTrackId())
                .name(track.getName())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .coverUrl(track.getCoverUrl())
                .duration(track.getDuration())
                .format(track.getFormat())
                .fileSize(track.getFileSize())
                .trackNumber(track.getTrackNumber())
                .hasLyric(Boolean.TRUE.equals(track.getHasLyric()))
                .lyricUrl(track.getLyricUrl())
                .build();
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\A\\p{Space}+|\\p{Space}+\\z", "");
    }
}
