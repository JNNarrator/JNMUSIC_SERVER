<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, VideoPlay, Loading } from '@element-plus/icons-vue'
import { usePlayerStore, type Track } from '../stores/player'

const player = usePlayerStore()
const tracks = ref<Track[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keyword = ref('')
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function fetchTracks() {
  loading.value = true
  try {
    const q = keyword.value.trim()
    const url = q
      ? `/music/api/v1/tracks/app/search?q=${encodeURIComponent(q)}&page=${page.value}&pageSize=${pageSize}`
      : `/music/api/v1/tracks/app?page=${page.value}&pageSize=${pageSize}`
    const res = await fetch(url)
    const payload = await res.json()
    if (!payload.success) {
      ElMessage.error(payload.error?.message || '加载失败')
      tracks.value = []
      total.value = 0
      return
    }
    tracks.value = payload.data.items as Track[]
    total.value = payload.data.total ?? tracks.value.length
  } catch (e) {
    ElMessage.error('网络异常，请稍后重试')
    tracks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function playAll(startIndex = 0) {
  const playable = tracks.value.filter((t) => t.mediaUrl)
  if (!playable.length) {
    ElMessage.warning('暂无可播放的曲目')
    return
  }
  const startTrack = tracks.value[startIndex]
  const realIndex = playable.findIndex((t) => t.trackId === startTrack?.trackId)
  player.setQueue(playable, realIndex >= 0 ? realIndex : 0)
}

// 点击整行的行为：若已是当前曲目则切换播放/暂停，否则从该曲开始播放。
function onRowActivate(track: Track, idx: number) {
  if (!track.mediaUrl) {
    ElMessage.warning('该曲目暂不可播放')
    return
  }
  if (player.currentTrack?.trackId === track.trackId) {
    player.toggle()
    return
  }
  playAll(idx)
}

function onSearch() { page.value = 1; fetchTracks() }
function onPageChange(p: number) { page.value = p; fetchTracks() }

function formatSize(bytes?: number) {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`
}

onMounted(fetchTracks)
</script>

<template>
  <section class="library">
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

    <div v-if="loading" class="skeleton">
      <div v-for="n in 6" :key="n" class="row-skel" />
    </div>

    <el-empty
      v-else-if="!tracks.length"
      description="书架空空，先添几张唱片吧"
      :image-size="80"
    />

    <ol v-else class="track-rows" role="list">
      <li
        v-for="(track, idx) in tracks"
        :key="track.trackId"
        class="row"
        :class="{ active: player.currentTrack?.trackId === track.trackId, disabled: !track.mediaUrl }"
        role="button"
        tabindex="0"
        :aria-disabled="!track.mediaUrl"
        :aria-label="`播放 ${track.name}`"
        @click="onRowActivate(track, idx)"
        @keydown.enter.prevent="onRowActivate(track, idx)"
        @keydown.space.prevent="onRowActivate(track, idx)"
      >
        <div class="row-play" aria-hidden="true">
          <span
            v-if="player.currentTrack?.trackId === track.trackId && player.isPlaying"
            class="wave"
          >
            <i /><i /><i /><i />
          </span>
          <template v-else>
            <span class="num">{{ (page - 1) * pageSize + idx + 1 }}</span>
            <span class="hover-play">
              <el-icon :size="18"><VideoPlay /></el-icon>
            </span>
          </template>
        </div>
        <div class="row-main">
          <p class="row-name">{{ track.name }}</p>
          <p class="row-artist">{{ track.artist || '未知艺人' }}</p>
        </div>
        <div class="row-meta">
          <span v-if="track.format" class="tag">{{ track.format.toUpperCase() }}</span>
          <span v-if="track.fileSize" class="size">{{ formatSize(track.fileSize) }}</span>
          <span v-if="!track.mediaUrl" class="unavailable">
            <el-icon><Loading /></el-icon>暂不可播放
          </span>
        </div>
      </li>
    </ol>

    <footer v-if="total > pageSize" class="pager">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        :page-count="totalPages"
        layout="prev, pager, next"
        background
        hide-on-single-page
        @current-change="onPageChange"
      />
    </footer>
  </section>
</template>

<style scoped>
.library { display: flex; flex-direction: column; gap: 22px; }

.library-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

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

.head-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.search-input { width: 240px; }

.play-all {
  color: var(--jn-accent-ink) !important;
  font-weight: 600 !important;
  padding: 10px 20px !important;
  box-shadow: 0 12px 30px var(--jn-glow);
}

.skeleton { display: grid; gap: 12px; }
.row-skel {
  height: 58px;
  border-radius: 10px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
@keyframes skel { 0% { background-position: 200% 0 } 100% { background-position: -200% 0 } }

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
}

.row:hover { background: var(--jn-row-hover); }
.row.active { background: var(--jn-row-active); }
.row.active .row-name { color: var(--jn-accent); }
.row.disabled { opacity: 0.55; }

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

.unavailable {
  display: inline-flex; align-items: center; gap: 4px;
  color: var(--jn-danger);
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

@media (max-width: 720px) {
  .library-head {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  .head-actions {
    width: 100%;
  }
  .search-input { flex: 1; width: auto; }
  .row { grid-template-columns: 36px 1fr auto; gap: 12px; padding: 10px 4px; }
  .row-meta .size { display: none; }
  .row-name { font-size: 14.5px; }
  .row-artist { font-size: 12.5px; }
}
</style>
