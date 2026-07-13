package com.jn.music.track.service.impl;

import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.LanzouSessionException;
import com.jn.music.lanzou.dto.LanzouDirectLink;
import com.jn.music.lanzou.dto.LanzouFile;
import com.jn.music.lanzou.dto.LanzouFolder;
import com.jn.music.lanzou.dto.LanzouPageResult;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从蓝奏云根目录读取音频文件作为音乐库数据源。
 * 目标：只读，没有增删；文件按 "作者 - 歌名.扩展名" 惯例解析元数据。
 */
@Service
public class TrackServiceImpl implements TrackService {

    private static final Logger log = LoggerFactory.getLogger(TrackServiceImpl.class);

    /** 支持的音频后缀（小写）。 */
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "flac", "mp3", "wav", "aac", "m4a", "ogg", "opus", "ape"
    );

    private static final String ROOT_FOLDER_ID = "-1";
    /** 蓝奏云根目录一次最多能拉的文件数（前端不做真正翻页时，把常见几百首装下）。 */
    private static final int MAX_PAGES = 20;

    /** 直链缓存有效期（3.5 小时，直链本身 4 小时有效）。 */
    private static final Duration CACHE_TTL = Duration.ofHours(3).plusMinutes(30);

    /** 直链缓存：fileId -> CachedLink。 */
    private final ConcurrentHashMap<String, CachedLink> directLinkCache = new ConcurrentHashMap<>();
    /** 歌词内容缓存：trackId -> 歌词文本（3.5 小时有效）。 */
    private final ConcurrentHashMap<String, String> lyricsContentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lyricsCacheExpiry = new ConcurrentHashMap<>();
    /** 歌词映射缓存：normalizedStem -> lyricFileId（随文件列表一起过期）。 */
    private volatile Map<String, String> cachedLyricsMap;
    private volatile Instant lyricsMapExpiresAt = Instant.EPOCH;
    /** 共享 OkHttpClient，避免每次歌词请求都新建连接池。 */
    private static final okhttp3.OkHttpClient sharedLyricsClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    /** 文件列表缓存（5 分钟，避免每次请求都翻页）。 */
    private static final Duration FILE_LIST_CACHE_TTL = Duration.ofMinutes(5);
    private volatile List<LanzouFile> cachedAllFiles;
    private volatile Instant fileListExpiresAt = Instant.EPOCH;

    /** 并发预取线程池。 */
    private final ExecutorService prefetchPool = Executors.newFixedThreadPool(4);

    private final LanzouApiClient lanzouClient;

    public TrackServiceImpl(LanzouApiClient lanzouClient) {
        this.lanzouClient = lanzouClient;
    }

    /** 缓存条目。 */
    private record CachedLink(String url, String format, Instant expiresAt) {
        boolean isValid() { return Instant.now().isBefore(expiresAt); }
    }

    @Override
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize) {
        int p = normalize(page, 1);
        int ps = normalize(pageSize, 20);

        List<TrackSummaryDTO> all = loadAllAudioSummaries();
        return paginate(all, p, ps);
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
            if (containsIgnoreCase(t.getName(), lower)
                    || containsIgnoreCase(t.getArtist(), lower)
                    || containsIgnoreCase(t.getAlbum(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, p, ps);
    }

    @Override
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize) {
        int p = normalize(page, 1);
        int ps = normalize(pageSize, 20);

        List<TrackWithUrlDTO> all = loadAllAudioWithUrl();
        return paginate(all, p, ps);
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
            if (containsIgnoreCase(t.getName(), lower)
                    || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, p, ps);
    }

    @Override
    public TrackDTO getTrackById(String trackId) {
        String id = requireTrackId(trackId);
        for (LanzouFile f : loadAllAudioFiles()) {
            if (id.equals(f.id())) {
                ParsedName pn = parseName(f.name());
                return TrackDTO.builder()
                        .trackId(f.id())
                        .name(pn.name())
                        .artist(pn.artist())
                        .format(pn.format())
                        .fileSize(f.size())
                        .hasLyric(false)
                        .build();
            }
        }
        return null;
    }

    @Override
    public PageResponse<TrackDTO> getTracksByIds(List<String> ids) {
        List<TrackDTO> items = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return PageResponse.<TrackDTO>builder()
                    .items(items).page(1).pageSize(0).total(0L).hasMore(false).build();
        }
        List<LanzouFile> files = loadAllAudioFiles();
        for (String id : ids) {
            for (LanzouFile f : files) {
                if (f.id().equals(id)) {
                    ParsedName pn = parseName(f.name());
                    items.add(TrackDTO.builder()
                            .trackId(f.id())
                            .name(pn.name())
                            .artist(pn.artist())
                            .format(pn.format())
                            .fileSize(f.size())
                            .hasLyric(false)
                            .build());
                    break;
                }
            }
        }
        return PageResponse.<TrackDTO>builder()
                .items(items).page(1).pageSize(items.size())
                .total((long) items.size()).hasMore(false).build();
    }

    @Override
    public MediaUrlDTO getMediaUrl(String trackId) {
        String id = requireTrackId(trackId);

        // 先查缓存
        CachedLink cached = directLinkCache.get(id);
        if (cached != null && cached.isValid()) {
            return MediaUrlDTO.builder()
                    .trackId(id)
                    .mediaUrl(cached.url())
                    .format(cached.format())
                    .expiresAt(OffsetDateTime.ofInstant(cached.expiresAt(), ZoneOffset.UTC))
                    .build();
        }

        // 找到对应的文件（用于确定 format），不存在直接返回 404 语义
        String format = "";
        for (LanzouFile f : loadAllAudioFiles()) {
            if (id.equals(f.id())) {
                format = parseName(f.name()).format();
                break;
            }
        }
        if (format.isEmpty()) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }

        LanzouDirectLink link;
        try {
            link = lanzouClient.getFileDownloadLink(id);
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取蓝奏云直链失败: " + e.getMessage());
        }

        // 写入缓存
        Instant expiry = Instant.now().plus(CACHE_TTL);
        directLinkCache.put(id, new CachedLink(link.url(), format, expiry));

        return MediaUrlDTO.builder()
                .trackId(id)
                .mediaUrl(link.url())
                .format(format)
                .expiresAt(OffsetDateTime.ofInstant(link.expiresAt(), ZoneOffset.UTC))
                .build();
    }

    /**
     * 后台预取指定歌曲的直链并缓存，不阻塞调用方。
     */
    public void prefetchDirectLink(String trackId, String format) {
        CompletableFuture.runAsync(() -> {
            try {
                CachedLink cached = directLinkCache.get(trackId);
                if (cached != null && cached.isValid()) return;
                LanzouDirectLink link = lanzouClient.getFileDownloadLink(trackId);
                Instant expiry = Instant.now().plus(CACHE_TTL);
                directLinkCache.put(trackId, new CachedLink(link.url(), format, expiry));
            } catch (Exception ignored) {
                // 预取失败不影响主流程
            }
        }, prefetchPool);
    }

    @Override
    public String getLyrics(String trackId) {
        String id = requireTrackId(trackId);

        // 1. 命中歌词内容缓存 → 直接返回
        Instant expiry = lyricsCacheExpiry.get(id);
        if (expiry != null && Instant.now().isBefore(expiry) && lyricsContentCache.containsKey(id)) {
            return lyricsContentCache.get(id);
        }

        // 2. 找到这首歌的文件名 stem
        String targetStem = null;
        for (LanzouFile f : loadAllAudioFiles()) {
            if (id.equals(f.id())) {
                int dot = f.name().lastIndexOf('.');
                targetStem = dot > 0 ? f.name().substring(0, dot) : f.name();
                break;
            }
        }
        if (targetStem == null) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }

        // 3. 在歌词文件中找匹配的 stem（使用缓存的映射）
        String lyricFileId = loadLyricsMap().get(normalizeStem(targetStem));
        if (lyricFileId == null) {
            // 无歌词也缓存空串，避免重复查找
            lyricsContentCache.put(id, "");
            lyricsCacheExpiry.put(id, Instant.now().plus(CACHE_TTL));
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND, "该歌曲暂无歌词");
        }

        // 4. 获取直链并下载歌词内容
        try {
            LanzouDirectLink link = lanzouClient.getFileDownloadLink(lyricFileId);
            okhttp3.Request req = new okhttp3.Request.Builder().url(link.url()).build();
            try (okhttp3.Response resp = sharedLyricsClient.newCall(req).execute()) {
                String content = resp.body() != null ? resp.body().string() : "";
                // 写入缓存
                lyricsContentCache.put(id, content);
                lyricsCacheExpiry.put(id, Instant.now().plus(CACHE_TTL));
                return content;
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取歌词失败: " + e.getMessage());
        }
    }

    // ==================== 内部辅助 ====================

    /** 加载蓝奏云根目录全部文件（带 5 分钟缓存）。 */
    private List<LanzouFile> loadAllLanzouFiles() {
        if (cachedAllFiles != null && Instant.now().isBefore(fileListExpiresAt)) {
            return cachedAllFiles;
        }
        List<LanzouFile> out = new ArrayList<>();
        try {
            loadFilesRecursively(ROOT_FOLDER_ID, out);
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "蓝奏云读取失败: " + e.getMessage());
        }
        log.info("蓝奏云扫描完成：共 {} 个文件（含非音频）", out.size());
        cachedAllFiles = out;
        fileListExpiresAt = Instant.now().plus(FILE_LIST_CACHE_TTL);
        return out;
    }

    /** 递归扫描指定文件夹及其所有子文件夹中的文件。 */
    private void loadFilesRecursively(String folderId, List<LanzouFile> out) throws LanzouSessionException {
        for (int page = 1; page <= MAX_PAGES; page++) {
            LanzouPageResult r = lanzouClient.listFiles(folderId, page);
            if (r == null) { log.debug("folder {} page {} returned null", folderId, page); break; }
            int fileCount = r.files() != null ? r.files().size() : 0;
            if (r.files() != null) out.addAll(r.files());
            log.debug("folder {} page {}: {} files, {} folders", folderId, page, fileCount,
                    r.folders() != null ? r.folders().size() : 0);
            // 第一页时递归扫描子文件夹
            if (page == 1 && r.folders() != null) {
                for (LanzouFolder sub : r.folders()) {
                    log.debug("scanning subfolder: {} ({})", sub.name(), sub.id());
                    loadFilesRecursively(sub.id(), out);
                }
            }
            // 蓝奏云分页不保证满页，只在真正无数据时停止
            if (fileCount == 0) break;
        }
    }

    /** 只取音频文件。 */
    private List<LanzouFile> loadAllAudioFiles() {
        List<LanzouFile> out = new ArrayList<>();
        for (LanzouFile f : loadAllLanzouFiles()) {
            if (isAudio(f.name())) out.add(f);
        }
        return out;
    }

    /** 归一化 stem：去掉 "-" 周围的空格，使 "双笙 - 江南游记" 和 "双笙-江南游记" 匹配。 */
    private static String normalizeStem(String stem) {
        return stem.trim().replaceAll("\s*-\s*", "-");
    }

    /** 构建歌词 stem -> fileId 映射（stem = 去扩展名的文件名）。 */
    private Map<String, String> loadLyricsMap() {
        if (cachedLyricsMap != null && Instant.now().isBefore(lyricsMapExpiresAt)) {
            return cachedLyricsMap;
        }
        Map<String, String> map = new java.util.HashMap<>();
        for (LanzouFile f : loadAllLanzouFiles()) {
            ParsedName pn = parseName(f.name());
            if (pn.isLyric()) {
                int dot = f.name().lastIndexOf('.');
                String stem = dot > 0 ? f.name().substring(0, dot) : f.name();
                map.put(normalizeStem(stem), f.id());
            }
        }
        cachedLyricsMap = map;
        lyricsMapExpiresAt = Instant.now().plus(FILE_LIST_CACHE_TTL);
        return map;
    }

    private List<TrackSummaryDTO> loadAllAudioSummaries() {
        Map<String, String> lyricsMap = loadLyricsMap();
        List<TrackSummaryDTO> out = new ArrayList<>();
        for (LanzouFile f : loadAllAudioFiles()) {
            ParsedName pn = parseName(f.name());
            int dot = f.name().lastIndexOf('.');
            String stem = dot > 0 ? f.name().substring(0, dot) : f.name();
            out.add(TrackSummaryDTO.builder()
                    .trackId(f.id())
                    .name(pn.name())
                    .artist(pn.artist())
                    .format(pn.format())
                    .fileSize(f.size())
                    .hasLyric(lyricsMap.containsKey(normalizeStem(stem)))
                    .build());
        }
        return out;
    }

    /** 加载所有音频文件并获取播放直链。 */
    private List<TrackWithUrlDTO> loadAllAudioWithUrl() {
        List<TrackWithUrlDTO> out = new ArrayList<>();
        for (LanzouFile f : loadAllAudioFiles()) {
            ParsedName pn = parseName(f.name());
            try {
                LanzouDirectLink link = lanzouClient.getFileDownloadLink(f.id());
                out.add(TrackWithUrlDTO.builder()
                        .trackId(f.id())
                        .name(pn.name())
                        .artist(pn.artist())
                        .format(pn.format())
                        .fileSize(f.size())
                        .mediaUrl(link.url())
                        .urlExpiresAt(OffsetDateTime.ofInstant(link.expiresAt(), ZoneOffset.UTC))
                        .build());
            } catch (LanzouSessionException e) {
                // 如果获取直链失败，仍然返回基本信息，但不包含URL
                out.add(TrackWithUrlDTO.builder()
                        .trackId(f.id())
                        .name(pn.name())
                        .artist(pn.artist())
                        .format(pn.format())
                        .fileSize(f.size())
                        .build());
            }
        }
        return out;
    }

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

    /** 从文件名解析 artist / name / format。支持 "作者 - 歌名.ext"；无 dash 时 artist=null。 */
    static ParsedName parseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return new ParsedName("", null, "", false);
        }
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        boolean isLyric = ext.equals("txt");

        // 以第一个 "-" 分割：前面是歌手，后面是歌名，忽略空格
        int dash = stem.indexOf('-');
        if (dash > 0) {
            String artist = stem.substring(0, dash).trim();
            String name = stem.substring(dash + 1).trim();
            if (!artist.isEmpty() && !name.isEmpty()) {
                return new ParsedName(name, artist, ext, isLyric);
            }
        }
        return new ParsedName(stem, null, ext, isLyric);
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

    /** 解析后的文件名三元组。 */
    record ParsedName(String name, String artist, String format, boolean isLyric) {}
}
