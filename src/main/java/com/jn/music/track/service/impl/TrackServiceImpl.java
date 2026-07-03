package com.jn.music.track.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jn.music.common.PageResponse;
import com.jn.music.common.config.FileServerProperties;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
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
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * P0 音乐库服务实现，当前仅覆盖元数据读取与播放地址拼接。
 */
@Service
public class TrackServiceImpl extends ServiceImpl<TrackMapper, Track> implements TrackService {

    private FileServerProperties fileServerProperties = new FileServerProperties();

    @Autowired
    public void setFileServerProperties(FileServerProperties fileServerProperties) {
        this.fileServerProperties = fileServerProperties;
    }

    @Override
    public PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize) {
        String normalizedKeyword = trimToEmpty(keyword);
        if (normalizedKeyword.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        // 核心：当前阶段用中文 LIKE 覆盖歌曲、歌手、专辑；拼音索引留到后续搜索升级。
        String likePattern = "%" + normalizedKeyword + "%";
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
        String normalizedTrackId = requireTrackId(trackId);
        Track track = lambdaQuery().eq(Track::getTrackId, normalizedTrackId).one();
        if (track == null) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }
        return toDto(track);
    }

    @Override
    public PageResponse<TrackDTO> getTracksByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return PageResponse.<TrackDTO>builder()
                    .items(List.of())
                    .page(1)
                    .pageSize(0)
                    .total(0L)
                    .hasMore(false)
                    .build();
        }
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
        String normalizedTrackId = requireTrackId(trackId);
        MediaQuality mediaQuality = parseQuality(quality);
        Track track = lambdaQuery()
                .select(Track::getTrackId, Track::getFormat)
                .eq(Track::getTrackId, normalizedTrackId)
                .one();
        if (track == null) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }

        String extension = resolveMediaExtension(track.getFormat(), mediaQuality);
        return MediaUrlDTO.builder()
                .trackId(normalizedTrackId)
                .mediaUrl(fileServerProperties.publicUrl("/audio/" + normalizedTrackId + "." + extension))
                .format(mediaQuality.getCode())
                .expiresAt(OffsetDateTime.now().plusHours(24).withOffsetSameInstant(ZoneOffset.UTC))
                .build();
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
                .coverUrl(resolvePublicResourceUrl(track.getCoverUrl()))
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
                .coverUrl(resolvePublicResourceUrl(track.getCoverUrl()))
                .duration(track.getDuration())
                .format(track.getFormat())
                .fileSize(track.getFileSize())
                .trackNumber(track.getTrackNumber())
                .hasLyric(Boolean.TRUE.equals(track.getHasLyric()))
                .lyricUrl(resolvePublicResourceUrl(track.getLyricUrl()))
                .build();
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\A\\p{Space}+|\\p{Space}+\\z", "");
    }

    private static String requireTrackId(String trackId) {
        String normalizedTrackId = trimToEmpty(trackId);
        if (normalizedTrackId.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "trackId 不能为空");
        }
        return normalizedTrackId;
    }

    private static MediaQuality parseQuality(String quality) {
        String normalizedQuality = trimToEmpty(quality);
        if (normalizedQuality.isEmpty()) {
            return MediaQuality.FLAC;
        }
        for (MediaQuality mediaQuality : MediaQuality.values()) {
            if (mediaQuality.getCode().equalsIgnoreCase(normalizedQuality)) {
                return mediaQuality;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的音质: " + quality);
    }

    private static String resolveMediaExtension(String storedFormat, MediaQuality mediaQuality) {
        if (mediaQuality == MediaQuality.FLAC) {
            String normalizedFormat = StringUtils.hasText(storedFormat)
                    ? storedFormat.trim().toLowerCase(Locale.ROOT)
                    : MediaQuality.FLAC.getCode();
            return normalizedFormat;
        }
        return mediaQuality.getCode();
    }

    private String resolvePublicResourceUrl(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            return null;
        }
        String trimmedPath = resourcePath.trim();
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return trimmedPath;
        }
        return fileServerProperties.publicUrl(trimmedPath);
    }
}
