package com.jn.music.track.service.impl;

import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.storage.MusicStorage;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TrackServiceImpl implements TrackService {

    private record SongFolder(String folderId, String folderName, StorageFile audioFile, StorageFile lyricFile) {
        ParsedName parseFolderName() {
            if (folderName == null || folderName.isBlank()) {
                return new ParsedName("", null, "", false);
            }
            int dash = folderName.indexOf('-');
            if (dash > 0) {
                String artist = folderName.substring(0, dash).trim();
                String name = folderName.substring(dash + 1).trim();
                if (!artist.isEmpty() && !name.isEmpty()) {
                    return new ParsedName(name, artist, audioFile != null ? getExtension(audioFile.name()) : "", false);
                }
            }
            return new ParsedName(folderName, null, audioFile != null ? getExtension(audioFile.name()) : "", false);
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
    private static final Duration SONG_FOLDERS_TTL = Duration.ofMinutes(5);
    private static final Duration LYRICS_TTL = Duration.ofHours(3).plusMinutes(30);

    private static final okhttp3.OkHttpClient sharedLyricsClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // 歌曲文件夹缓存
    private volatile List<SongFolder> cachedSongFolders;
    private volatile Instant songFoldersExpiresAt = Instant.EPOCH;
    
    // 歌词缓存
    private final ConcurrentHashMap<String, String> lyricsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lyricsCacheExpiry = new ConcurrentHashMap<>();

    private final MusicStorage musicStorage;

    public TrackServiceImpl(MusicStorage musicStorage) {
        this.musicStorage = musicStorage;
    }

    // ==================== 公开接口 ====================

    @Override
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize) {
        return listTracks(page, pageSize, false);
    }

    @Override
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize, boolean refresh) {
        if (refresh) {
            clearSongFoldersCache();
        }
        int p = normalize(page, 1);
        int ps = normalize(pageSize, 20);
        return paginate(loadAllAudioSummaries(), p, ps);
    }

    @Override
    public PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize) {
        String kw = trim(keyword);
        if (kw.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        int p = normalize(page, 1);
        int ps = normalize(pageSize, 20);
        String lower = kw.toLowerCase(Locale.ROOT);

        List<TrackSummaryDTO> matched = new ArrayList<>();
        for (TrackSummaryDTO t : loadAllAudioSummaries()) {
            if (containsIgnoreCase(t.getName(), lower) || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, p, ps);
    }

    @Override
    public TrackDTO getTrackById(String trackId) {
        String id = requireTrackId(trackId);
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) {
                ParsedName pn = sf.parseFolderName();
                return TrackDTO.builder()
                        .trackId(sf.audioFile().id())
                        .name(pn.name())
                        .artist(pn.artist())
                        .format(pn.format())
                        .fileSize(sf.audioFile().size())
                        .hasLyric(sf.lyricFile() != null)
                        .build();
            }
        }
        throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    @Override
    public PageResponse<TrackDTO> getTracksByIds(List<String> ids) {
        List<SongFolder> songFolders = loadSongFolders();
        List<TrackDTO> items = new ArrayList<>();
        for (String id : ids) {
            for (SongFolder sf : songFolders) {
                if (sf.audioFile().id().equals(id)) {
                    ParsedName pn = sf.parseFolderName();
                    items.add(TrackDTO.builder()
                            .trackId(sf.audioFile().id())
                            .name(pn.name())
                            .artist(pn.artist())
                            .format(pn.format())
                            .fileSize(sf.audioFile().size())
                            .hasLyric(sf.lyricFile() != null)
                            .build());
                    break;
                }
            }
        }
        return PageResponse.<TrackDTO>builder()
                .items(items).page(1).pageSize(0).total((long) items.size()).hasMore(false).build();
    }

    @Override
    public MediaUrlDTO getMediaUrl(String trackId) {
        String id = requireTrackId(trackId);
        String format = "";
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) {
                format = sf.parseFolderName().format();
                break;
            }
        }
        try {
            String url = musicStorage.getDownloadUrl(id);
            return MediaUrlDTO.builder()
                    .trackId(id)
                    .mediaUrl(url)
                    .format(format)
                    .expiresAt(OffsetDateTime.now().plusHours(4))
                    .build();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取播放链接失败: " + e.getMessage());
        }
    }

    @Override
    public String getLyrics(String trackId) {
        String id = requireTrackId(trackId);
        
        // 检查歌词缓存
        Instant expiry = lyricsCacheExpiry.get(id);
        if (expiry != null && Instant.now().isBefore(expiry)) {
            String cached = lyricsCache.get(id);
            if (cached != null) {
                if (cached.isEmpty()) {
                    throw new BusinessException(ErrorCode.TRACK_NOT_FOUND, "该歌曲暂无歌词");
                }
                return cached;
            }
        }

        // 在歌曲文件夹中查找歌词
        for (SongFolder sf : loadSongFolders()) {
            if (sf.audioFile().id().equals(id) && sf.lyricFile() != null) {
                try {
                    String url = musicStorage.getDownloadUrl(sf.lyricFile().id());
                    String lyrics = downloadText(url);
                    lyricsCache.put(id, lyrics);
                    lyricsCacheExpiry.put(id, Instant.now().plus(LYRICS_TTL));
                    return lyrics;
                } catch (Exception e) {
                    log.warn("下载歌词失败: {}", e.getMessage());
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "歌词下载失败");
                }
            }
        }

        // 无歌词，缓存空串避免重复查找
        lyricsCache.put(id, "");
        lyricsCacheExpiry.put(id, Instant.now().plus(LYRICS_TTL));
        throw new BusinessException(ErrorCode.TRACK_NOT_FOUND, "该歌曲暂无歌词");
    }

    @Override
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize) {
        return listTracksWithUrl(page, pageSize, false);
    }

    @Override
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize, boolean refresh) {
        if (refresh) {
            clearSongFoldersCache();
        }
        int p = normalize(page, 1);
        int ps = normalize(pageSize, 20);
        return paginate(loadAllAudioWithUrl(), p, ps);
    }

    @Override
    public PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize) {
        String kw = trim(keyword);
        if (kw.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        int p = normalize(page, 1);
        int ps = normalize(pageSize, 20);
        String lower = kw.toLowerCase(Locale.ROOT);

        List<TrackWithUrlDTO> matched = new ArrayList<>();
        for (TrackWithUrlDTO t : loadAllAudioWithUrl()) {
            if (containsIgnoreCase(t.getName(), lower) || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, p, ps);
    }

    // ==================== 内部方法 ====================

    private void clearSongFoldersCache() {
        cachedSongFolders = null;
        songFoldersExpiresAt = Instant.EPOCH;
        log.info("歌曲文件夹缓存已清除");
    }

    private List<SongFolder> loadSongFolders() {
        if (cachedSongFolders != null && Instant.now().isBefore(songFoldersExpiresAt)) {
            return cachedSongFolders;
        }
        List<SongFolder> folders = new ArrayList<>();
        try {
            loadSongFoldersRecursively(ROOT_FOLDER_ID, folders);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "存储读取失败: " + e.getMessage());
        }
        log.info("存储扫描完成：共 {} 个歌曲文件夹 [{}]", folders.size(), musicStorage.getStorageName());
        cachedSongFolders = folders;
        songFoldersExpiresAt = Instant.now().plus(SONG_FOLDERS_TTL);
        return folders;
    }

    private void loadSongFoldersRecursively(String folderId, List<SongFolder> out) {
        for (int page = 1; page <= MAX_PAGES; page++) {
            StorageListResult r = musicStorage.listFiles(folderId, page);
            if (r == null) break;

            if (page == 1 && !r.folders().isEmpty()) {
                for (StorageFolder sub : r.folders()) {
                    SongFolder songFolder = scanSongFolder(sub.id(), sub.name());
                    if (songFolder != null) {
                        out.add(songFolder);
                    } else {
                        loadSongFoldersRecursively(sub.id(), out);
                    }
                }
            }
            if (page == 1) break;
        }
    }

    private SongFolder scanSongFolder(String folderId, String folderName) {
        StorageListResult r = musicStorage.listFiles(folderId, 1);
        if (r == null || r.files().isEmpty()) return null;

        StorageFile audioFile = null;
        StorageFile lyricFile = null;
        for (StorageFile f : r.files()) {
            if (isAudio(f.name())) {
                audioFile = f;
            } else if (f.name().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                lyricFile = f;
            }
        }
        if (audioFile != null) {
            return new SongFolder(folderId, folderName, audioFile, lyricFile);
        }
        return null;
    }

    private List<TrackSummaryDTO> loadAllAudioSummaries() {
        List<SongFolder> songFolders = loadSongFolders();
        List<TrackSummaryDTO> out = new ArrayList<>();
        for (SongFolder sf : songFolders) {
            ParsedName pn = sf.parseFolderName();
            out.add(TrackSummaryDTO.builder()
                    .trackId(sf.audioFile().id())
                    .name(pn.name())
                    .artist(pn.artist())
                    .format(pn.format())
                    .fileSize(sf.audioFile().size())
                    .hasLyric(sf.lyricFile() != null)
                    .build());
        }
        return out;
    }

    private List<TrackWithUrlDTO> loadAllAudioWithUrl() {
        List<SongFolder> songFolders = loadSongFolders();
        List<TrackWithUrlDTO> out = new ArrayList<>();
        for (SongFolder sf : songFolders) {
            ParsedName pn = sf.parseFolderName();
            try {
                String url = musicStorage.getDownloadUrl(sf.audioFile().id());
                out.add(TrackWithUrlDTO.builder()
                        .trackId(sf.audioFile().id())
                        .name(pn.name())
                        .artist(pn.artist())
                        .format(pn.format())
                        .fileSize(sf.audioFile().size())
                        .mediaUrl(url)
                        .urlExpiresAt(OffsetDateTime.now().plusHours(4))
                        .build());
            } catch (Exception e) {
                out.add(TrackWithUrlDTO.builder()
                        .trackId(sf.audioFile().id())
                        .name(pn.name())
                        .artist(pn.artist())
                        .format(pn.format())
                        .fileSize(sf.audioFile().size())
                        .build());
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

    // ==================== 工具方法 ====================

    private static <T> PageResponse<T> paginate(List<T> all, int page, int pageSize) {
        int total = all.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return PageResponse.<T>builder()
                .items(new ArrayList<>(all.subList(from, to)))
                .page(page)
                .pageSize(pageSize)
                .total((long) total)
                .hasMore(to < total)
                .build();
    }

    private static boolean isAudio(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return false;
        return AUDIO_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }

    private static int normalize(Integer v, int fallback) {
        if (v == null || v < 1) return fallback;
        return v;
    }

    private static String requireTrackId(String trackId) {
        String s = trim(trackId);
        if (s.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "trackId 不能为空");
        return s;
    }

    private static boolean containsIgnoreCase(String source, String kwLower) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(kwLower);
    }

    record ParsedName(String name, String artist, String format, boolean isLyric) {}
}
