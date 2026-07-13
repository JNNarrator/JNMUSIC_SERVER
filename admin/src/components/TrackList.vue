<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { showToast } from 'vant'
import { ElIcon } from 'element-plus'
import { Search, VideoPlay, Refresh, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { usePlayerStore, fetchMediaUrls, type Track } from '../stores/player'
import { useThemeStore } from '../stores/theme'

const player = usePlayerStore()
const theme = useThemeStore()
const tracks = ref<Track[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keyword = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(true)
const error = ref<string | null>(null)
const trackMediaStatus = ref<Record<string, 'loading' | 'ready' | 'error'>>({})


async function preloadAllUrls() {
  if (!tracks.value.length) return
  const ids = tracks.value.map(t => t.trackId)
  const st: Record<string, 'loading' | 'ready' | 'error'> = {}
  ids.forEach(id => { st[id] = 'loading' })
  trackMediaStatus.value = { ...st }
  try {
    await fetchMediaUrls(ids)
    ids.forEach(id => { st[id] = 'ready' })
  } catch {
    ids.forEach(id => { st[id] = 'error' })
  }
  trackMediaStatus.value = { ...st }
}

// --- pull-to-refresh ---
const pullRef = ref<HTMLElement | null>(null)
const scrollRef = ref<HTMLElement | null>(null)
const pulling = ref(false)
const pullDistance = ref(0)
const refreshing = ref(false)
const PULL_THRESHOLD = 80
let startY = 0
let skipPull = false

async function fetchTracks(append = false) {
  if (append) {
    loadingMore.value = true
  } else {
    loading.value = true
  }
  error.value = null
  try {
    const q = keyword.value.trim()
    const url = q
      ? `/music/api/v1/tracks/search?q=${encodeURIComponent(q)}&page=${page.value}&pageSize=${pageSize}`
      : `/music/api/v1/tracks?page=${page.value}&pageSize=${pageSize}`
    const res = await fetch(url)
    const payload = await res.json()
    if (!payload.success) {
      error.value = payload.error?.message || '加载失败'
      if (!append) { tracks.value = []; total.value = 0 }
      return
    }
    const items = payload.data.items as Track[]
    total.value = payload.data.total ?? items.length
    if (append) {
      tracks.value.push(...items)
    } else {
      tracks.value = items
    }
    hasMore.value = items.length === pageSize
    preloadAllUrls()
  } catch (e) {
    error.value = '网络异常，请检查网络后重试'
    if (!append) { tracks.value = []; total.value = 0 }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function retryFetch() {
  error.value = null
  fetchTracks()
}

function playAll(startIndex = 0) {
  if (!tracks.value.length) {
    showToast('暂无可播放的曲目')
    return
  }
  player.setQueue(tracks.value, startIndex)
}

function onRowActivate(track: Track, idx: number) {
  if (player.currentTrack?.trackId === track.trackId) {
    return
  }
  playAll(idx)
}

function onSearch() {
  page.value = 1
  tracks.value = []
  hasMore.value = true
  fetchTracks()
}

async function doRefresh() {
  refreshing.value = true
  page.value = 1
  tracks.value = []
  hasMore.value = true
  await fetchTracks()
  refreshing.value = false
}

function onScroll(e: Event) {
  const el = e.target as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200) {
    loadMore()
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  page.value++
  await fetchTracks(true)
}

function isScrolledFromTarget(target: EventTarget | null): boolean {
  let el = target as HTMLElement | null
  while (el && el !== pullRef.value) {
    if (el.scrollTop > 0) return true
    el = el.parentElement
  }
  return false
}

function onTouchStart(e: TouchEvent) {
  if (refreshing.value) return
  skipPull = isScrolledFromTarget(e.target)
  startY = e.touches[0].clientY
  pulling.value = false
}

function onTouchMove(e: TouchEvent) {
  if (refreshing.value || skipPull) return
  const dy = e.touches[0].clientY - startY
  if (dy <= 0) {
    pullDistance.value = 0
    pulling.value = false
    return
  }
  pullDistance.value = Math.min(dy, 140)
  pulling.value = true
}

function onTouchEnd() {
  if (refreshing.value) return
  if (pulling.value && pullDistance.value >= PULL_THRESHOLD) {
    pullDistance.value = 52
    doRefresh()
  } else {
    pullDistance.value = 0
  }
  pulling.value = false
}

function bindTouch(el: HTMLElement | null) {
  if (!el) return
  el.addEventListener('touchstart', onTouchStart, { passive: true })
  el.addEventListener('touchmove', onTouchMove, { passive: true })
  el.addEventListener('touchend', onTouchEnd, { passive: true })
}

function unbindTouch(el: HTMLElement | null) {
  if (!el) return
  el.removeEventListener('touchstart', onTouchStart)
  el.removeEventListener('touchmove', onTouchMove)
  el.removeEventListener('touchend', onTouchEnd)
}

onMounted(() => {
  fetchTracks()
  bindTouch(scrollRef.value)
})

onBeforeUnmount(() => {
  unbindTouch(scrollRef.value)
})
</script>

<template>
  <section ref="pullRef" class="library">
    <header class="library-head">
      <div class="head-title">
        <p class="eyebrow">// Tonight's Rotation</p>
        <h2>唱针落下之处</h2>
        <p class="lead">精选曲目，点开任意一行，整张歌单便成为你今晚的电台节目。</p>
      </div>
      <div class="head-actions">
        <div class="search-input">
          <el-icon :size="16"><Search /></el-icon>
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索曲名或艺人"
            @keyup.enter="onSearch"
          />
        </div>
        <button class="play-all" @click="playAll(0)" :disabled="!tracks.length">
          <el-icon :size="16"><VideoPlay /></el-icon>
          <span>播放全部</span>
        </button>
      </div>
    </header>

    <!-- Pull indicator -->
    <div
      class="pull-indicator"
      :class="{ active: pullDistance >= PULL_THRESHOLD, refreshing }"
      :style="{ height: `${pullDistance}px` }"
    >
      <el-icon class="pull-icon"><Refresh /></el-icon>
      <span v-if="refreshing">正在刷新…</span>
      <span v-else-if="pullDistance >= PULL_THRESHOLD">释放刷新</span>
      <span v-else>下拉刷新</span>
    </div>

    <!-- Track list -->
    <div
      ref="scrollRef"
      class="track-scroll"
      @scroll="onScroll"
    >
      <!-- Loading skeleton -->
      <div v-if="loading" class="skeleton">
        <div v-for="n in 5" :key="n" class="skeleton-row" />
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="error-state">
        <p>{{ error }}</p>
        <button class="retry-btn" @click="retryFetch">
          <el-icon><Refresh /></el-icon>
          <span>重试</span>
        </button>
      </div>

      <!-- Empty state -->
      <div v-else-if="!tracks.length" class="empty-state">
        <p>暂无曲目</p>
      </div>

      <!-- Track rows -->
      <div v-else class="track-rows">
        <div
          v-for="(track, idx) in tracks"
          :key="track.trackId"
          class="row"
          :class="{ active: player.currentTrack?.trackId === track.trackId }"
          @click="onRowActivate(track, idx)"
        >
          <div class="row-play">
            <span v-if="player.currentTrack?.trackId === track.trackId && player.isPlaying" class="wave">
              <i /><i /><i /><i /><i />
            </span>
            <span v-else class="num">{{ idx + 1 }}</span>
            <span class="hover-play">
              <el-icon :size="16"><VideoPlay /></el-icon>
            </span>
          </div>
          <div class="row-main">
            <p class="row-name">{{ track.name }}</p>
            <p class="row-artist">{{ track.artist || '未知艺人' }}</p>
          </div>
          <div class="row-meta">
            <span class="track-status" :title="trackMediaStatus[track.trackId] === 'loading' ? '加载中…' : trackMediaStatus[track.trackId] === 'ready' ? '就绪' : '加载失败'">
              <el-icon v-if="trackMediaStatus[track.trackId] === 'loading'" :size="14" class="spin"><Loading /></el-icon>
              <el-icon v-else-if="trackMediaStatus[track.trackId] === 'ready'" :size="14" class="status-ready"><CircleCheck /></el-icon>
              <el-icon v-else-if="trackMediaStatus[track.trackId] === 'error'" :size="14" class="status-error"><CircleClose /></el-icon>
            </span>
          </div>
        </div>
      </div>

      <!-- Load more -->
      <div v-if="loadingMore" class="load-more">
        <el-icon class="spin"><Refresh /></el-icon>
        <span>加载中…</span>
      </div>
      <div v-else-if="!hasMore && tracks.length > 0" class="load-more no-more">
        <span>没有更多了</span>
      </div>
    </div>

  </section>
</template>

<style scoped>
.library {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.library-head {
  flex-shrink: 0;
  padding: 0 0 16px;
}

.head-title {
  margin-bottom: 16px;
}

.eyebrow {
  margin: 0 0 6px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  text-transform: uppercase;
}

.head-title h2 {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-weight: 500;
  font-style: italic;
  font-size: clamp(30px, 4.2vw, 52px);
  line-height: 1.02;
  color: var(--jn-ink-strong);
}

.lead {
  margin: 12px 0 0;
  max-width: 42ch;
  color: var(--jn-ink-dim);
  font-size: 14.5px;
  line-height: 1.55;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 320px;
  min-width: 180px;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--jn-hair);
  border-radius: 999px;
  background: var(--jn-input-bg);
  transition: border-color 0.15s;
}
.search-input:focus-within {
  border-color: var(--jn-accent);
}
.search-input input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--jn-ink);
  font-size: 13px;
}
.search-input input::placeholder {
  color: var(--jn-ink-muted);
}
.search-input .el-icon {
  color: var(--jn-ink-dim);
  flex-shrink: 0;
}

.play-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 999px;
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 8px 24px var(--jn-glow);
}
.play-all:hover:not(:disabled) {
  transform: scale(1.04);
  box-shadow: 0 12px 32px var(--jn-glow);
}
.play-all:active:not(:disabled) {
  transform: scale(0.96);
}
.play-all:disabled {
  opacity: 0.5;
  cursor: default;
}

/* Pull indicator */
.pull-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 0;
  overflow: hidden;
  color: var(--jn-ink-dim);
  font-size: 13px;
  transition: height 0.25s ease;
}
.pull-indicator.active {
  color: var(--jn-accent);
}
.pull-indicator.refreshing .pull-icon {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Track scroll */
.track-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.track-scroll::-webkit-scrollbar {
  display: none;
}

/* Skeleton */
.skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 0;
}
.skeleton-row {
  height: 52px;
  background: var(--jn-row-hover);
  border-radius: 8px;
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* Error state */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 48px 0;
  color: var(--jn-ink-dim);
}
.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--jn-hair);
  border-radius: 8px;
  background: transparent;
  color: var(--jn-ink);
  cursor: pointer;
  transition: background 0.15s;
}
.retry-btn:hover {
  background: var(--jn-row-hover);
}

/* Empty state */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  color: var(--jn-ink-muted);
  font-size: 14px;
}

/* Track rows */
.track-rows {
  border-top: 1px solid var(--jn-hair);
}

.row {
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 12px 6px;
  border-bottom: 1px solid var(--jn-hair);
  transition: background 0.15s ease;
  cursor: default;
}

.row:hover { background: var(--jn-row-hover); }
.row.active { 
  background: var(--jn-row-active); 
  animation: row-glow 2s ease-in-out infinite;
}
.row.active .row-name { color: var(--jn-accent); }
@keyframes row-glow {
  0%, 100% { background: var(--jn-row-active); }
  50% { background: color-mix(in oklab, var(--jn-accent) 8%, transparent); }
}

.row-play {
  position: relative;
  width: 36px; height: 36px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--jn-ink);
  display: inline-flex;
  align-items: center; justify-content: center;
  cursor: pointer;
  font-family: 'IBM Plex Mono', monospace;
}

.row-play .num { font-size: 13px; color: var(--jn-ink-dim); }
.row-play .hover-play {
  position: absolute; inset: 0;
  display: none; align-items: center; justify-content: center;
  color: var(--jn-accent);
}
.row:hover .row-play .num { visibility: hidden; }
.row:hover .row-play .hover-play { display: inline-flex; }
.row.active:hover .row-play .hover-play { display: none; }
.row.active:hover .row-play .num { visibility: visible; }
.row.active .row-play { color: var(--jn-accent); }

.wave { display: inline-flex; align-items: flex-end; gap: 2px; height: 18px; }
.wave i {
  width: 3px; border-radius: 2px;
  animation: pulse 1.2s ease-in-out infinite;
  background: linear-gradient(to top, var(--jn-accent), var(--jn-accent-strong));
}
.wave i:nth-child(1) { height: 55%; animation-delay: -0.5s; }
.wave i:nth-child(2) { height: 100%; animation-delay: -0.25s; }
.wave i:nth-child(3) { height: 45%; animation-delay: 0s; }
.wave i:nth-child(4) { height: 80%; animation-delay: -0.35s; }
.wave i:nth-child(5) { height: 60%; animation-delay: -0.15s; }
@keyframes pulse { 
  0%, 100% { transform: scaleY(0.3); opacity: 0.7; } 
  50% { transform: scaleY(1); opacity: 1; } 
}

.row-main { min-width: 0; }
.row-name {
  margin: 0; font-size: 15px; color: var(--jn-ink);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  font-weight: 500;
}
.row-artist {
  margin: 3px 0 0; font-size: 13px;
  color: var(--jn-ink-dim);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.row-meta {
  display: inline-flex; align-items: center; gap: 10px;
  color: var(--jn-ink-dim);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
}

.tag {
  padding: 3px 7px;
  border: 1px solid var(--jn-hair-strong);
  border-radius: 4px;
}

.track-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px; height: 22px;
}
.track-status .status-ready { color: var(--jn-accent); }
.track-status .status-error { color: var(--jn-danger); }
.track-status .spin { color: var(--jn-ink-dim); animation: spin 0.8s linear infinite; }

.load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px 0;
  color: var(--jn-ink-dim);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 12px;
}
.spin { animation: spin 0.8s linear infinite; }
.no-more { color: var(--jn-ink-muted); }

@media (max-width: 720px) {
  .library { 
    padding: 0 16px 0;
    height: 100%;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .library-head {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 32px 0 16px;
    flex-shrink: 0;
  }
  .head-actions { width: 100%; }
  .search-input { flex: 1; width: auto; }
  .track-scroll {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    -webkit-overflow-scrolling: touch;
    padding-bottom: calc(20px + env(safe-area-inset-bottom, 34px));
  }
  .row { grid-template-columns: 36px 1fr auto; gap: 12px; padding: 10px 4px; }
  .row-meta .size { display: none; }
  .row-name { font-size: 14.5px; }
  .row-artist { font-size: 12.5px; }
}
</style>
