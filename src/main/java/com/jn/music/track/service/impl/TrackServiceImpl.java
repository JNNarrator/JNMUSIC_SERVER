package com.jn.music.track.service.impl;

import com.jn.music.common.PageResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.LanzouSessionException;
import com.jn.music.lanzou.dto.LanzouDirectLink;
import com.jn.music.lanzou.dto.LanzouFile;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

/**
 * 从蓝奏云根目录读取音频文件作为音乐库数据源。
 * 目标：只读，没有增删；文件按 "作者 - 歌名.扩展名" 惯例解析元数据。
 */
@Service
public class TrackServiceImpl implements TrackService {

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

    // ==================== 内部辅助 ====================

    /** 拉取蓝奏云根目录下的所有音频文件（跨页汇总；MAX_PAGES 上限保护）。 */
    private List<LanzouFile> loadAllAudioFiles() {
        List<LanzouFile> out = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                LanzouPageResult r = lanzouClient.listFiles(ROOT_FOLDER_ID, page);
                if (r == null || r.files() == null || r.files().isEmpty()) break;
                for (LanzouFile f : r.files()) {
                    if (isAudio(f.name())) out.add(f);
                }
                // 蓝奏云一页 20 条，不足 20 说明已到末页
                if (r.files().size() < 20) break;
            }
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "蓝奏云读取失败: " + e.getMessage());
        }
        return out;
    }

    private List<TrackSummaryDTO> loadAllAudioSummaries() {
        List<TrackSummaryDTO> out = new ArrayList<>();
        for (LanzouFile f : loadAllAudioFiles()) {
            ParsedName pn = parseName(f.name());
            out.add(TrackSummaryDTO.builder()
                    .trackId(f.id())
                    .name(pn.name())
                    .artist(pn.artist())
                    .format(pn.format())
                    .fileSize(f.size())
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
            return new ParsedName("", null, "");
        }
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";

        // 蓝奏云保存的文件名会用 " - " 分隔作者和歌名（也支持 " -" / "- " 变体）
        int sep = stem.indexOf(" - ");
        if (sep < 0) sep = stem.indexOf(" -");
        if (sep < 0) sep = stem.indexOf("- ");
        if (sep > 0) {
            String artist = stem.substring(0, sep).trim();
            String name = stem.substring(sep + " - ".length()).trim();
            if (name.isEmpty()) name = stem;
            return new ParsedName(name, artist.isEmpty() ? null : artist, ext);
        }
        return new ParsedName(stem, null, ext);
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
    record ParsedName(String name, String artist, String format) {}
}
