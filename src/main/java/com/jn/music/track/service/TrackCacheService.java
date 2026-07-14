package com.jn.music.track.service;

import com.jn.music.track.domain.Track;
import com.jn.music.track.mapper.TrackMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class TrackCacheService {

    private static final Logger log = LoggerFactory.getLogger(TrackCacheService.class);
    private static final String CACHE_NAME = "mediaUrls";

    private final TrackMapper trackMapper;
    private final TrackService trackService;
    private final CacheManager cacheManager;

    private final AtomicInteger refreshTotal = new AtomicInteger(0);
    private final AtomicInteger refreshCompleted = new AtomicInteger(0);
    private volatile boolean refreshing = false;
    private volatile boolean initialized = false;

    public TrackCacheService(TrackMapper trackMapper, TrackService trackService, CacheManager cacheManager) {
        this.trackMapper = trackMapper;
        this.trackService = trackService;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    void onStartup() { initCache(); }

    @Async
    public void initCache() {
        log.info("TrackCacheService: 从 MySQL 预热 L1 缓存...");
        List<Track> all = trackMapper.selectList(null);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            for (Track t : all) {
                if (t.getMediaUrl() != null && !t.getMediaUrl().isBlank()
                        && t.getUrlExpiresAt() != null && t.getUrlExpiresAt().isAfter(OffsetDateTime.now())) {
                    cache.put(t.getTrackId(), t.getMediaUrl());
                }
            }
        }
        initialized = true;
        log.info("TrackCacheService: L1 预热完成 {} 首", all.size());
        if (all.stream().anyMatch(t -> t.getMediaUrl() == null || t.getUrlExpiresAt() == null || t.getUrlExpiresAt().isBefore(OffsetDateTime.now()))) {
            refreshAll();
        }
    }

    @Scheduled(cron = "0 0 */4 * * ?")
    public void scheduledRefresh() { refreshAll(); }

    /** 全量刷新：从 lanzou 获取所有 trackId → 逐个拉取直链 → 写入/更新 MySQL + L1 */
    public void refreshAll() {
        if (refreshing) return;
        synchronized (this) { if (refreshing) return; refreshing = true; }
        try {
            trackMapper.clearAllMediaUrls();
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) cache.clear();

            List<String> ids = trackService.getAllTrackIds();
            refreshTotal.set(ids.size());
            refreshCompleted.set(0);

            for (String id : ids) {
                try {
                    var dto = trackService.getMediaUrl(id);
                    // upsert: 新增或更新 MySQL
                    var t = trackMapper.selectById(id);
                    if (t == null) {
                        t = new Track();
                        t.setTrackId(id);
                        t.setName(id);
                        t.setArtist("");
                        t.setDuration(0);
                        t.setFormat("");
                        t.setHasLyric(false);
                        t.setFileSize(0L);
                        t.setMediaUrl(dto.getMediaUrl());
                        t.setUrlExpiresAt(dto.getExpiresAt());
                        trackMapper.insert(t);
                    } else {
                        t.setMediaUrl(dto.getMediaUrl());
                        t.setUrlExpiresAt(dto.getExpiresAt());
                        trackMapper.updateById(t);
                    }
                    if (cache != null) cache.put(id, dto.getMediaUrl());
                } catch (Exception e) {
                    log.warn("TrackCacheService: {} 失败: {}", id, e.getMessage());
                }
                refreshCompleted.incrementAndGet();
            }
            log.info("TrackCacheService: 刷新完成 {}/{}", refreshCompleted.get(), ids.size());
        } finally { refreshing = false; }
    }

    public void refreshTrackUrl(String trackId) {
        try {
            var dto = trackService.getMediaUrl(trackId);
            var t = trackMapper.selectById(trackId);
            if (t != null) {
                t.setMediaUrl(dto.getMediaUrl());
                t.setUrlExpiresAt(dto.getExpiresAt());
                trackMapper.updateById(t);
            }
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) cache.put(trackId, dto.getMediaUrl());
        } catch (Exception e) {
            log.warn("TrackCacheService: 单首刷新失败 {}", trackId);
        }
    }

    @Async
    public void manualRefresh() { refreshAll(); }

    public boolean isInitialized() { return initialized; }
    public boolean isRefreshing() { return refreshing; }
    public Map<String, Object> getStatus() {
        return Map.of("total", refreshTotal.get(), "completed", refreshCompleted.get(), "inProgress", refreshing, "initialized", initialized);
    }
}
