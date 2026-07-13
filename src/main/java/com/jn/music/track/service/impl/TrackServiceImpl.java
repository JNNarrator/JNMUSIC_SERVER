package com.jn.music.track.service.impl;

import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.storage.MusicStorage;
import com.jn.music.track.mapper.TrackMapper;
import com.jn.music.track.domain.Track;
import com.jn.music.storage.StorageFile;
import com.jn.music.storage.StorageFolder;
import com.jn.music.storage.StorageListResult;
import com.jn.music.track.dto.MediaUrlDTO;
import com.jn.music.track.dto.TrackDTO;
import com.jn.music.track.dto.TrackSummaryDTO;
import com.jn.music.track.dto.TrackWithUrlDTO;
import com.jn.music.track.service.TrackService;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TrackServiceImpl implements TrackService {

    private record SongFolder(String folderId, String folderName, StorageFile audioFile, StorageFile lyricFile) {
        ParsedName parseFolderName() {
            if (folderName == null || folderName.isBlank()) {
                return new ParsedName("", null, "", false);
            }
            String text = folderName.trim();
            int dash = text.indexOf('-');
            if (dash > 0) {
                String artist = splitCamelCase(text.substring(0, dash).trim());
                String name = splitCamelCase(text.substring(dash + 1).trim());
                if (!artist.isEmpty() && !name.isEmpty()) {
                    return new ParsedName(name, artist, audioFile != null ? getExtension(audioFile.name()) : "", false);
                }
            }
            return new ParsedName(splitCamelCase(text), null, audioFile != null ? getExtension(audioFile.name()) : "", false);
        }

        private static String splitCamelCase(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(s.charAt(i - 1))) {
                    sb.append(' ');
                }
                sb.append(c);
            }
            return sb.toString();
        }

        private static String getExtension(String fileName) {
            if (fileName == null) return "";
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TrackServiceImpl.class);
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "flac", "mp3", "wav", "aac", "m4a", "ogg", "opus", "ape"
    );
    private static final String ROOT_FOLDER_ID = "-1";
    private static final int MAX_PAGES = 20;

    private final MusicStorage musicStorage;
    private final TrackMapper trackMapper;

    public TrackServiceImpl(MusicStorage musicStorage, TrackMapper trackMapper) {
        this.musicStorage = musicStorage;
        this.trackMapper = trackMapper;
    }

    @Override
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize) {
        return listTracks(page, pageSize, false);
    }

    @Override
    @CacheEvict(value = "songFolders", allEntries = true, condition = "#refresh")
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize, boolean refresh) {
        return paginate(loadAllAudioSummaries(), normalize(page, 1), normalize(pageSize, 20));
    }

    @Override
    public PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize) {
        String kw = trim(keyword);
        if (kw.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        String lower = kw.toLowerCase(Locale.ROOT);
        List<TrackSummaryDTO> matched = new ArrayList<>();
        for (TrackSummaryDTO t : loadAllAudioSummaries()) {
            if (containsIgnoreCase(t.getName(), lower) || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, normalize(page, 1), normalize(pageSize, 20));
    }

    @Override
    public TrackDTO getTrackById(String trackId) {
        String id = requireTrackId(trackId);
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) {
                ParsedName pn = sf.parseFolderName();
                return TrackDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size()).hasLyric(sf.lyricFile() != null).build();
            }
        }
        throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    @Override
    public PageResponse<TrackDTO> getTracksByIds(List<String> ids) {
        List<TrackDTO> items = new ArrayList<>();
        for (String id : ids) {
            for (SongFolder sf : loadSongFolders()) {
                if (sf.audioFile().id().equals(id)) {
                    ParsedName pn = sf.parseFolderName();
                    items.add(TrackDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                            .format(pn.format()).fileSize(sf.audioFile().size()).hasLyric(sf.lyricFile() != null).build());
                    break;
                }
            }
        }
        return PageResponse.<TrackDTO>builder().items(items).page(1).pageSize(0).total((long) items.size()).hasMore(false).build();
    }

    @Override
    @Cacheable(value = "mediaUrls", key = "#trackId")
    public MediaUrlDTO getMediaUrl(String trackId) {
        String id = requireTrackId(trackId);
        // L2: 先查 MySQL 缓存的直链
        var cachedTrack = trackMapper.selectById(id);
        if (cachedTrack != null && cachedTrack.getMediaUrl() != null && !cachedTrack.getMediaUrl().isBlank()
                && cachedTrack.getUrlExpiresAt() != null && cachedTrack.getUrlExpiresAt().isAfter(OffsetDateTime.now())) {
            // 有效缓存 → 直接返回，不调蓝奏云
            String fmt = cachedTrack.getFormat() != null ? cachedTrack.getFormat() : "";
            return MediaUrlDTO.builder().trackId(id).mediaUrl(cachedTrack.getMediaUrl()).format(fmt)
                    .expiresAt(cachedTrack.getUrlExpiresAt()).build();
        }
        // 缓存失效 → 调蓝奏云 → 回写 MySQL
        String format = "";
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) { format = sf.parseFolderName().format(); break; }
        }
        try {
            var dto = MediaUrlDTO.builder().trackId(id).mediaUrl(musicStorage.getDownloadUrl(id)).format(format)
                    .expiresAt(OffsetDateTime.now().plusHours(4)).build();
            // 回写 MySQL
            if (cachedTrack != null) {
                cachedTrack.setMediaUrl(dto.getMediaUrl());
                cachedTrack.setUrlExpiresAt(dto.getExpiresAt());
                cachedTrack.setFormat(format);
                trackMapper.updateById(cachedTrack);
            }
            return dto;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取播放链接失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, MediaUrlDTO> getMediaUrls(List<String> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) return Map.of();
        Map<String, MediaUrlDTO> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        // L2: 先查 MySQL
        for (String id : trackIds) {
            var ct = trackMapper.selectById(id);
            if (ct != null && ct.getMediaUrl() != null && !ct.getMediaUrl().isBlank()
                    && ct.getUrlExpiresAt() != null && ct.getUrlExpiresAt().isAfter(OffsetDateTime.now())) {
                String fmt = ct.getFormat() != null ? ct.getFormat() : "";
                result.put(id, MediaUrlDTO.builder().trackId(id).mediaUrl(ct.getMediaUrl()).format(fmt)
                        .expiresAt(ct.getUrlExpiresAt()).build());
            } else {
                missing.add(id);
            }
        }
        // 未命中 → 调蓝奏云批量获取
        if (!missing.isEmpty()) {
            Map<String, String> urlMap = musicStorage.getDownloadUrls(missing);
            for (SongFolder sf : loadSongFolders()) {
                String id = sf.audioFile().id();
                if (urlMap.containsKey(id)) {
                    var dto = MediaUrlDTO.builder().trackId(id).mediaUrl(urlMap.get(id))
                            .format(sf.parseFolderName().format()).expiresAt(OffsetDateTime.now().plusHours(4)).build();
                    result.put(id, dto);
                    // 回写 MySQL
                    var ct = trackMapper.selectById(id);
                    if (ct != null) {
                        ct.setMediaUrl(dto.getMediaUrl());
                        ct.setUrlExpiresAt(dto.getExpiresAt());
                        trackMapper.updateById(ct);
                    }
                }
            }
        }
        return result;
    }

    @Override
    @Cacheable(value = "lyrics", key = "#trackId")
    public String getLyrics(String trackId) {
        String id = requireTrackId(trackId);
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) {
                if (sf.lyricFile() == null) {
                    throw new BusinessException(ErrorCode.TRACK_NOT_FOUND, "该歌曲暂无歌词");
                }
                try {
                    return downloadText(musicStorage.getDownloadUrl(sf.lyricFile().id()));
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取歌词失败: " + e.getMessage());
                }
            }
        }
        throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    @Override
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize) {
        return listTracksWithUrl(page, pageSize, false);
    }

    @Override
    @CacheEvict(value = "songFolders", allEntries = true, condition = "#refresh")
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize, boolean refresh) {
        return paginate(loadAllAudioWithUrl(), normalize(page, 1), normalize(pageSize, 20));
    }

    @Override
    public PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize) {
        String kw = trim(keyword);
        if (kw.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        String lower = kw.toLowerCase(Locale.ROOT);
        List<TrackWithUrlDTO> matched = new ArrayList<>();
        for (TrackWithUrlDTO t : loadAllAudioWithUrl()) {
            if (containsIgnoreCase(t.getName(), lower) || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, normalize(page, 1), normalize(pageSize, 20));
    }

    @Cacheable(value = "songFolders", key = "'all'")
    public List<SongFolder> loadSongFolders() {
        List<SongFolder> folders = new ArrayList<>();
        loadSongFoldersRecursively(ROOT_FOLDER_ID, folders);
        return folders;
    }

    private void loadSongFoldersRecursively(String folderId, List<SongFolder> out) {
        for (int page = 1; page <= MAX_PAGES; page++) {
            StorageListResult r = musicStorage.listFiles(folderId, page);
            if (r == null || (r.files().isEmpty() && r.folders().isEmpty())) break;
            for (StorageFolder f : r.folders()) {
                SongFolder sf = scanSongFolder(f.id(), f.name());
                if (sf != null) out.add(sf);
                else loadSongFoldersRecursively(f.id(), out);
            }
            if (page == 1) break;
        }
    }

    private SongFolder scanSongFolder(String folderId, String folderName) {
        StorageListResult r = musicStorage.listFiles(folderId, 1);
        if (r == null || r.files().isEmpty()) return null;
        StorageFile audioFile = null, lyricFile = null;
        for (StorageFile f : r.files()) {
            if (isAudio(f.name())) audioFile = f;
            else if (f.name().toLowerCase(Locale.ROOT).endsWith(".txt")) lyricFile = f;
        }
        return audioFile != null ? new SongFolder(folderId, folderName, audioFile, lyricFile) : null;
    }

    private List<TrackSummaryDTO> loadAllAudioSummaries() {
        // 先从 MySQL 加载所有缓存的直链
        java.util.Map<String, Track> cacheMap = new java.util.HashMap<>();
        for (com.jn.music.track.domain.Track t : trackMapper.selectList(null)) {
            if (t.getMediaUrl() != null && !t.getMediaUrl().isBlank()) {
                cacheMap.put(t.getTrackId(), t);
            }
        }
        List<TrackSummaryDTO> out = new ArrayList<>();
        for (SongFolder sf : loadSongFolders()) {
            ParsedName pn = sf.parseFolderName();
            var cached = cacheMap.get(sf.audioFile().id());
            out.add(TrackSummaryDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                    .format(pn.format()).fileSize(sf.audioFile().size()).hasLyric(sf.lyricFile() != null)
                    .mediaUrl(cached != null ? cached.getMediaUrl() : null)
                    .urlExpiresAt(cached != null ? cached.getUrlExpiresAt() : null)
                    .build());
        }
        return out;
    }

    private List<TrackWithUrlDTO> loadAllAudioWithUrl() {
        List<TrackWithUrlDTO> out = new ArrayList<>();
        for (SongFolder sf : loadSongFolders()) {
            ParsedName pn = sf.parseFolderName();
            try {
                String url = musicStorage.getDownloadUrl(sf.audioFile().id());
                out.add(TrackWithUrlDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size()).mediaUrl(url)
                        .urlExpiresAt(OffsetDateTime.now().plusHours(4)).build());
            } catch (Exception e) {
                out.add(TrackWithUrlDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size()).build());
            }
        }
        return out;
    }

    private String downloadText(String url) {
        okhttp3.Request req = new okhttp3.Request.Builder().url(url).build();
        try (okhttp3.Response resp = sharedLyricsClient.newCall(req).execute()) {
            return resp.body() != null ? resp.body().string() : "";
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "下载失败: " + e.getMessage());
        }
    }

    private static final okhttp3.OkHttpClient sharedLyricsClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private static <T> PageResponse<T> paginate(List<T> all, int page, int pageSize) {
        int total = all.size(), from = Math.min((page - 1) * pageSize, total), to = Math.min(from + pageSize, total);
        return PageResponse.<T>builder().items(new ArrayList<>(all.subList(from, to)))
                .page(page).pageSize(pageSize).total((long) total).hasMore(to < total).build();
    }

    private static boolean isAudio(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1 && AUDIO_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }
    private static int normalize(Integer v, int fallback) { return v == null || v < 1 ? fallback : v; }
    private static String requireTrackId(String trackId) {
        String s = trim(trackId);
        if (s.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "trackId 不能为空");
        return s;
    }
    private static boolean containsIgnoreCase(String source, String kwLower) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(kwLower);
    }

    record ParsedName(String name, String artist, String format, boolean isLyric) {}
    @Override
    public java.util.List<String> getAllTrackIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (SongFolder sf : loadSongFolders()) {
            ids.add(sf.audioFile().id());
        }
        return ids;
    }
}
