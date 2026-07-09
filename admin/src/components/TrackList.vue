<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, VideoPlay, Refresh, Document } from '@element-plus/icons-vue'
import { usePlayerStore, type Track } from '../stores/player'
import LyricsPanel from './LyricsPanel.vue'

const player = usePlayerStore()
const tracks = ref<Track[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keyword = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const lyricsTrackId = ref('')
const lyricsTrackName = ref('')
const showLyrics = ref(false)

// --- pull-to-refresh ---
const pullRef = ref<HTMLElement | null>(null)
const scrollRef = ref<HTMLElement | null>(null)
const pulling = ref(false)
const pullDistance = ref(0)
const refreshing = ref(false)
const PULL_THRESHOLD = 80
let startY = 0
let skipPull = false

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function fetchTracks() {
  loading.value = true
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
      tracks.value = []
      total.value = 0
      return
    }
    tracks.value = payload.data.items as Track[]
    total.value = payload.data.total ?? tracks.value.length
  } catch (e) {
    error.value = '网络异常，请检查网络后重试'
    tracks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function retryFetch() {
  error.value = null
  fetchTracks()
}

function playAll(startIndex = 0) {
  if (!tracks.value.length) {
    ElMessage.warning('暂无可播放的曲目')
    return
  }
  player.setQueue(tracks.value, startIndex)
}

function onRowActivate(track: Track, idx: number) {
  // 如果点击的是正在播放的歌曲，不做任何操作
  if (player.currentTrack?.trackId === track.trackId) {
    return
  }
  playAll(idx)
}

function openLyrics(trackId: string, name: string) {
  lyricsTrackId.value = trackId
  lyricsTrackName.value = name
  showLyrics.value = true
}

function onSearch() { page.value = 1; fetchTracks() }
function onPageChange(p: number) { page.value = p; fetchTracks() }

async function doRefresh() {
  refreshing.value = true
  page.value = 1
  await fetchTracks()
  refreshing.value = false
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

function formatSize(bytes?: number) {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`
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
        <p class="eyebrow">// Tonight&rsquo;s Rotation</p>
        <h2>唱针落下之处</h2>
        <p class="lead">精选曲目，点开任意一行，整张歌单便成为你今晚的电台节目。</p>
      </div>
      <div class="head-actions">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索曲名或艺人"
          :prefix-icon="Search"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button
          class="play-all"
          type="primary"
          round
          :icon="VideoPlay"
          :disabled="!tracks.length"
          @click="playAll(0)"
        >
          播放全部
        </el-button>
      </div>
    </header>

    <!-- 下拉指示器 -->
    <div
      class="pull-indicator"
      :class="{ active: pullDistance >= PULL_THRESHOLD, refreshing }"
      :style="{ height: `${pullDistance}px` }"
    >
      <el-icon :size="16"><Refresh /></el-icon>
      <span v-if="refreshing">刷新中…</span>
      <span v-else-if="pullDistance >= PULL_THRESHOLD">松手刷新</span>
      <span v-else>下拉刷新</span>
    </div>

    <!-- 骨架屏 -->
    <div v-if="loading && !refreshing" class="skeleton">
      <div v-for="n in pageSize" :key="n" class="row-skel">
        <div class="skel-play" />
        <div class="skel-main">
          <div class="skel-name" />
          <div class="skel-artist" />
        </div>
        <div class="skel-meta">
          <div class="skel-tag" />
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <p class="error-text">{{ error }}</p>
      <el-button
        class="retry-btn"
        type="primary"
        round
        :icon="Refresh"
        @click="retryFetch"
      >
        重新加载
      </el-button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!tracks.length && !loading" class="empty">
      <p>暂无曲目</p>
    </div>

    <!-- 歌曲列表 -->
    <div v-else ref="scrollRef" class="track-scroll">
      <ol class="track-rows">
        <li
          v-for="(track, idx) in tracks"
          :key="track.trackId"
          class="row"
          :class="{ active: player.currentTrack?.trackId === track.trackId }"
          @click="onRowActivate(track, idx)"
        >
          <button class="row-play" @click.stop="onRowActivate(track, idx)">
            <span v-if="player.currentTrack?.trackId === track.trackId && player.isPlaying" class="wave">
              <i /><i /><i /><i />
            </span>
            <span v-else class="num">{{ (page - 1) * pageSize + idx + 1 }}</span>
            <span class="hover-play"><el-icon><VideoPlay /></el-icon></span>
          </button>

          <div class="row-main">
            <p class="row-name">{{ track.name }}</p>
            <p class="row-artist">{{ track.artist || '未知艺人' }}</p>
          </div>

          <div class="row-meta">
            <span v-if="track.format" class="tag">{{ track.format.toUpperCase() }}</span>
            <span v-if="track.fileSize" class="size">{{ formatSize(track.fileSize) }}</span>
            <button
              v-if="track.hasLyric"
              class="lyrics-btn"
              title="查看歌词"
              @click.stop="openLyrics(track.trackId, track.name)"
            >
              <el-icon :size="14"><Document /></el-icon>
            </button>
          </div>
        </li>
      </ol>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- 歌词面板 -->
    <LyricsPanel
      v-model:visible="showLyrics"
      :track-id="lyricsTrackId"
      :track-name="lyricsTrackName"
    />
  </section>
</template>

<style scoped>
.library {
  position: relative;
  max-width: 860px;
  margin: 0 auto;
  padding: 0 24px 40px;
}

.library-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 60px 0 32px;
}

.head-title { flex: 1; min-width: 0; }
.head-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.search-input { width: 240px; }

.play-all {
  color: var(--jn-accent-ink) !important;
  font-weight: 600 !important;
  padding: 10px 20px !important;
  box-shadow: 0 12px 30px var(--jn-glow);
}

.skeleton { display: grid; gap: 0; border-top: 1px solid var(--jn-hair); }
.row-skel {
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 12px 6px;
  border-bottom: 1px solid var(--jn-hair);
}
.skel-play {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
.skel-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.skel-name {
  height: 16px;
  width: 60%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
.skel-artist {
  height: 12px;
  width: 40%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
.skel-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.skel-tag {
  width: 40px;
  height: 20px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
@keyframes skel { 0% { background-position: 200% 0 } 100% { background-position: -200% 0 } }

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 80px 0;
}

.error-text {
  font-size: 15px;
  color: var(--jn-danger);
  font-family: 'IBM Plex Mono', monospace;
}

.retry-btn {
  color: var(--jn-accent-ink) !important;
  font-weight: 600 !important;
  box-shadow: 0 8px 24px var(--jn-glow);
}

.track-rows {
  list-style: none; padding: 0; margin: 0;
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
.row.active { background: var(--jn-row-active); }
.row.active .row-name { color: var(--jn-accent); }

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
/* 播放中的行，悬停时隐藏hover-play，避免和wave重叠 */
.row.active:hover .row-play .hover-play { display: none; }
.row.active:hover .row-play .num { visibility: visible; }
.row.active .row-play { color: var(--jn-accent); }

.wave { display: inline-flex; align-items: flex-end; gap: 2px; height: 16px; }
.wave i {
  width: 3px; background: var(--jn-accent); border-radius: 2px;
  animation: pulse 1s ease-in-out infinite;
}
.wave i:nth-child(1) { height: 60%; animation-delay: -0.4s; }
.wave i:nth-child(2) { height: 100%; animation-delay: -0.2s; }
.wave i:nth-child(3) { height: 40%; animation-delay: 0s; }
.wave i:nth-child(4) { height: 75%; animation-delay: -0.3s; }
@keyframes pulse { 0%, 100% { transform: scaleY(0.35); } 50% { transform: scaleY(1); } }

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

.lyrics-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px; height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}
.lyrics-btn:hover {
  color: var(--jn-accent);
  background: var(--jn-row-hover);
}

.pager {
  display: flex;
  justify-content: center;
  padding-top: 14px;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--jn-ink-muted);
}

.track-scroll {
  scrollbar-width: none;
}
.track-scroll::-webkit-scrollbar {
  display: none;
}

.pull-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
  color: var(--jn-ink-dim);
  font-size: 13px;
  transition: height 0.25s ease;
}
.pull-indicator.active { color: var(--jn-accent); }
.pull-indicator.refreshing :deep(.el-icon) { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.eyebrow {
  margin: 0 0 8px;
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

@media (max-width: 720px) {
  .library { padding: 0 16px 24px; }
  .library-head {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 40px 0 24px;
  }
  .head-actions { width: 100%; }
  .search-input { flex: 1; width: auto; }
  .row { grid-template-columns: 36px 1fr auto; gap: 12px; padding: 10px 4px; }
  .row-meta .size { display: none; }
  .row-name { font-size: 14.5px; }
  .row-artist { font-size: 12.5px; }
}
</style>
