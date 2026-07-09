<script setup lang="ts">
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import {
  VideoPlay,
  VideoPause,
  DArrowLeft,
  DArrowRight,
  Sort,
  Refresh,
  RefreshRight,
  Mute,
  Close,
} from '@element-plus/icons-vue'
import { usePlayerStore, type PlayMode } from '../stores/player'
import { useUiStore } from '../stores/ui'
import { parseLrc, findCurrentLine, fetchLyricsCached } from '../utils/lrc'

const player = usePlayerStore()
const ui = useUiStore()

// -- LRC 解析 --
const rawLyrics = ref('')
const lyricLines = computed(() => parseLrc(rawLyrics.value))
const hasTimedLyrics = computed(() => lyricLines.value.length > 0)
const currentLineIdx = ref(-1)
const lyricsLoading = ref(false)

// 歌词容器引用
const lyricsContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])

function setLineRef(el: HTMLElement | null, idx: number) {
  if (el) lineRefs.value[idx] = el
}

// 获取歌词
async function fetchLyrics(trackId: string) {
  if (!trackId) return
  lyricsLoading.value = true
  const { raw } = await fetchLyricsCached(trackId)
  rawLyrics.value = raw
  lyricsLoading.value = false
}

// 监听切歌，重新获取歌词
watch(
  () => player.currentTrack?.trackId,
  (id) => {
    if (id && ui.showPlayerPage) fetchLyrics(id)
  },
  { immediate: true }
)

// 打开页面时如果已有当前歌曲，获取歌词
watch(() => ui.showPlayerPage, (show) => {
  if (show && player.currentTrack?.trackId) {
    fetchLyrics(player.currentTrack.trackId)
  }
})

// RAF 循环同步歌词
let rafId = 0
function syncLyrics() {
  if (lyricLines.value.length) {
    const idx = findCurrentLine(lyricLines.value, player.currentTime)
    if (idx !== currentLineIdx.value) {
      currentLineIdx.value = idx
      // 滚动到当前行
      nextTick(() => {
        const el = lineRefs.value[idx]
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      })
    }
  }
  rafId = requestAnimationFrame(syncLyrics)
}

watch(() => ui.showPlayerPage, (show) => {
  if (show) {
    rafId = requestAnimationFrame(syncLyrics)
  } else {
    cancelAnimationFrame(rafId)
  }
}, { immediate: true })

onUnmounted(() => cancelAnimationFrame(rafId))

// -- 格式化 --
function fmt(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const s = Math.floor(seconds)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${r.toString().padStart(2, '0')}`
}

// -- 进度 / 音量 --
const progress = computed({
  get: () => player.currentTime,
  set: (v: number) => player.seek(v),
})

const volumeValue = computed({
  get: () => Math.round(player.volume * 100),
  set: (v: number) => player.setVolume(v / 100),
})

// -- 播放模式 --
const MODE_META: Record<PlayMode, { label: string; icon: any }> = {
  list: { label: '列表循环', icon: RefreshRight },
  one: { label: '单曲循环', icon: Refresh },
  shuffle: { label: '随机播放', icon: Sort },
}
const modeMeta = computed(() => MODE_META[player.mode])

// -- 下滑手势 --
const dragOffset = ref(0)
const dragging = ref(false)
let startY = 0
let startTime = 0

function onTouchStart(e: TouchEvent) {
  // 仅在歌词区域顶部时才允许下滑
  const container = lyricsContainer.value
  if (container && container.scrollTop > 5) return
  startY = e.touches[0].clientY
  startTime = Date.now()
  dragging.value = true
}

function onTouchMove(e: TouchEvent) {
  if (!dragging.value) return
  const dy = e.touches[0].clientY - startY
  if (dy > 0) {
    dragOffset.value = dy
  }
}

function onTouchEnd() {
  if (!dragging.value) return
  const elapsed = Date.now() - startTime
  const velocity = dragOffset.value / Math.max(elapsed, 1)
  if (dragOffset.value > 100 || velocity > 0.5) {
    ui.closePlayerPage()
  }
  dragOffset.value = 0
  dragging.value = false
}

// 重置偏移（用于非触摸场景的关闭按钮）
function handleClose() {
  ui.closePlayerPage()
}
</script>

<template>
  <Transition name="player-page">
    <div
      v-if="ui.showPlayerPage"
      class="player-page"
      :style="dragging && dragOffset > 0 ? { transform: `translateY(${dragOffset}px)`, opacity: 1 - dragOffset / 600 } : {}"
      @touchstart.passive="onTouchStart"
      @touchmove.passive="onTouchMove"
      @touchend.passive="onTouchEnd"
    >
      <!-- 顶部：歌名歌手 -->
      <header class="pp-header">
        <div class="pp-track-info">
          <p class="pp-title">{{ player.currentTrack?.name || '未在播放' }}</p>
          <p class="pp-artist">{{ player.currentTrack?.artist || '—' }}</p>
        </div>
      </header>

      <!-- 中间：歌词区 -->
      <div ref="lyricsContainer" class="pp-lyrics">
        <template v-if="lyricsLoading">
          <div class="pp-lyrics-status">
            <div v-for="n in 5" :key="n" class="pp-skel" />
          </div>
        </template>
        <template v-else-if="hasTimedLyrics">
          <div class="pp-lyrics-pad-top" />
          <p
            v-for="(line, i) in lyricLines"
            :key="i"
            :ref="(el) => setLineRef(el as HTMLElement, i)"
            class="pp-line"
            :class="{ active: i === currentLineIdx, past: i < currentLineIdx }"
          >
            {{ line.text || '···' }}
          </p>
          <div class="pp-lyrics-pad-bottom" />
        </template>
        <template v-else-if="rawLyrics">
          <pre class="pp-lyrics-raw">{{ rawLyrics }}</pre>
        </template>
        <template v-else>
          <div class="pp-lyrics-empty">
            <p class="pp-lyrics-empty-text">暂无歌词</p>
          </div>
        </template>
      </div>

      <!-- 底部：控制区 -->
      <footer class="pp-controls">
        <!-- 进度条 -->
        <div class="pp-progress-row">
          <span class="pp-time">{{ fmt(player.currentTime) }}</span>
          <el-slider
            v-model="progress"
            class="pp-progress-slider"
            :min="0"
            :max="Math.max(0.1, player.duration)"
            :step="0.1"
            :show-tooltip="false"
            :disabled="!player.currentTrack"
          />
          <span class="pp-time">{{ fmt(player.duration) }}</span>
        </div>

        <!-- 主控区：模式 / 上一曲 / 播放·暂停 / 下一曲 / 音量 -->
        <div class="pp-main-ctl">
          <!-- 左侧：播放模式 -->
          <div class="pp-side pp-side-left">
            <el-tooltip :content="modeMeta.label" placement="top" :hide-after="800">
              <button
                class="pp-mode-btn"
                :class="{ active: true, ['mode-' + player.mode]: true }"
                :aria-label="'播放模式：' + modeMeta.label"
                @click.stop="player.cyclePlayMode"
              >
                <el-icon :size="20"><component :is="modeMeta.icon" /></el-icon>
                <span v-if="player.mode === 'one'" class="pp-mode-badge">1</span>
              </button>
            </el-tooltip>
          </div>

          <!-- 中心：上一曲 / 播放·暂停 / 下一曲 -->
          <div class="pp-center-btns">
            <el-tooltip content="上一曲" placement="top" :hide-after="800">
              <button
                class="pp-skip-btn"
                :disabled="!player.queue.length"
                aria-label="上一曲"
                @click.stop="player.prev"
              >
                <el-icon :size="24"><DArrowLeft /></el-icon>
              </button>
            </el-tooltip>

            <button
              class="pp-play-btn"
              :class="{ playing: player.isPlaying }"
              :disabled="!player.currentTrack"
              aria-label="播放或暂停"
              @click.stop="player.toggle"
            >
              <span class="pp-play-ring" />
              <el-icon :size="28" class="pp-play-icon">
                <VideoPause v-if="player.isPlaying" />
                <VideoPlay v-else />
              </el-icon>
            </button>

            <el-tooltip content="下一曲" placement="top" :hide-after="800">
              <button
                class="pp-skip-btn"
                :disabled="!player.queue.length"
                aria-label="下一曲"
                @click.stop="player.next(true)"
              >
                <el-icon :size="24"><DArrowRight /></el-icon>
              </button>
            </el-tooltip>
          </div>

          <!-- 右侧：音量（桌面端） -->
          <div class="pp-side pp-side-right">
            <div class="pp-volume">
              <el-icon :size="18" class="pp-vol-icon"><Mute /></el-icon>
              <el-slider
                v-model="volumeValue"
                class="pp-vol-slider"
                :min="0"
                :max="100"
                :show-tooltip="false"
                aria-label="音量"
              />
            </div>
          </div>
        </div>
      </footer>

      <!-- 收起按钮：右下角，拇指易达区 -->
      <button class="pp-collapse-btn" aria-label="收起" @click="handleClose">
        <el-icon :size="18"><Close /></el-icon>
      </button>
    </div>
  </Transition>
</template>

<style scoped>
/* 入场/退场动画 */
.player-page-enter-active,
.player-page-leave-active {
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.35s ease;
}
.player-page-enter-from {
  transform: translateY(100%);
  opacity: 0;
}
.player-page-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* 全屏页 */
.player-page {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  flex-direction: column;
  background: var(--jn-bg);
  color: var(--jn-ink);
  overflow: hidden;
  will-change: transform;
}

/* 顶部 header */
.pp-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 24px 8px;
  flex-shrink: 0;
}
.pp-collapse-btn {
  position: fixed;
  top: calc(16px + env(safe-area-inset-top));
  right: 24px;
  z-index: 51;
  width: 44px;
  height: 44px;
  border: 1.5px solid var(--jn-hair-strong);
  border-radius: 50%;
  background: var(--jn-bar-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, border-color 0.2s, transform 0.15s, background 0.2s;
}
.pp-collapse-btn:hover {
  color: var(--jn-ink-strong);
  border-color: var(--jn-ink-dim);
  background: var(--jn-bg-elev);
}
.pp-collapse-btn:active {
  transform: scale(0.9);
}
.pp-track-info {
  flex: 1;
  min-width: 0;
  text-align: center;
}
.pp-title {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-weight: 500;
  font-size: 18px;
  color: var(--jn-ink-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pp-artist {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--jn-ink-dim);
  font-family: 'IBM Plex Mono', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}


/* 歌词区 */
.pp-lyrics {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
}
.pp-lyrics::-webkit-scrollbar { display: none; }

.pp-lyrics-pad-top,
.pp-lyrics-pad-bottom {
  flex-shrink: 0;
  height: 40vh;
}

.pp-line {
  margin: 0;
  padding: 8px 16px;
  font-size: 15px;
  line-height: 1.8;
  color: var(--jn-ink-muted);
  text-align: center;
  transition: color 0.3s ease, font-size 0.3s ease, opacity 0.3s ease;
  max-width: 100%;
  word-break: break-word;
}
.pp-line.past {
  opacity: 0.35;
}
.pp-line.active {
  font-size: 20px;
  color: var(--jn-accent);
  text-shadow: 0 0 20px var(--jn-glow), 0 0 40px rgba(242, 177, 52, 0.15);
  font-weight: 500;
}

.pp-lyrics-status {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 40% 40px 0;
  width: 100%;
}
.pp-skel {
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
@keyframes skel {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.pp-lyrics-raw {
  margin: 20% 0 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 13px;
  line-height: 1.8;
  color: var(--jn-ink-dim);
  text-align: left;
  max-width: 500px;
}

.pp-lyrics-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pp-lyrics-empty-text {
  font-size: 15px;
  color: var(--jn-ink-muted);
  font-family: 'IBM Plex Mono', monospace;
}

/* 底部控制区 */
.pp-controls {
  flex-shrink: 0;
  padding: 16px 28px calc(24px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  background: linear-gradient(to top, rgba(16, 12, 17, 0.6) 0%, transparent 100%);
}

/* 进度条行 */
.pp-progress-row {
  display: grid;
  grid-template-columns: 44px 1fr 44px;
  align-items: center;
  gap: 14px;
  width: 100%;
  max-width: 520px;
}
.pp-time {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  text-align: center;
  letter-spacing: 0.03em;
}
.pp-progress-slider { width: 100%; }

/* 主控区：三栏布局 — 左模式 / 中心播放 / 右音量 */
.pp-main-ctl {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 520px;
  gap: 0;
}

.pp-side {
  flex: 1;
  display: flex;
  align-items: center;
}
.pp-side-left { justify-content: flex-start; }
.pp-side-right { justify-content: flex-end; }

/* 播放模式按钮 */
.pp-mode-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, background 0.2s;
}
.pp-mode-btn:hover {
  color: var(--jn-ink-strong);
  background: var(--jn-row-hover);
}
.pp-mode-btn.active { color: var(--jn-accent); }
.pp-mode-badge {
  position: absolute;
  top: 3px;
  right: 3px;
  font-size: 8px;
  font-family: 'IBM Plex Mono', monospace;
  color: var(--jn-accent-ink);
  background: var(--jn-accent);
  border-radius: 4px;
  padding: 0 3px;
  line-height: 1.4;
}

/* 中心播放按钮组 */
.pp-center-btns {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

/* 上一曲 / 下一曲 */
.pp-skip-btn {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, transform 0.15s, background 0.2s;
}
.pp-skip-btn:hover:not(:disabled) {
  color: var(--jn-ink-strong);
  background: var(--jn-row-hover);
  transform: scale(1.06);
}
.pp-skip-btn:active:not(:disabled) { transform: scale(0.95); }
.pp-skip-btn:disabled { opacity: 0.3; cursor: default; }

/* 播放 / 暂停 */
.pp-play-btn {
  position: relative;
  width: 64px;
  height: 64px;
  border: none;
  border-radius: 50%;
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.3s;
  box-shadow: 0 8px 32px var(--jn-glow);
}
.pp-play-btn:hover:not(:disabled) {
  transform: scale(1.06);
  box-shadow: 0 12px 40px var(--jn-glow), 0 0 0 3px var(--jn-accent-soft);
}
.pp-play-btn:active:not(:disabled) { transform: scale(0.96); }
.pp-play-btn:disabled { opacity: 0.4; cursor: default; }

/* 呼吸光环 */
.pp-play-ring {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 1.5px solid var(--jn-accent);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s;
}
.pp-play-btn.playing .pp-play-ring {
  opacity: 0.35;
  animation: pp-ring-pulse 2.4s ease-in-out infinite;
}
@keyframes pp-ring-pulse {
  0%, 100% { transform: scale(1); opacity: 0.35; }
  50% { transform: scale(1.12); opacity: 0.12; }
}

.pp-play-icon {
  position: relative;
  z-index: 1;
}

/* 音量 */
.pp-volume {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pp-vol-icon { color: var(--jn-ink-dim); }
.pp-vol-slider { width: 90px; }

/* 移动端 */
@media (max-width: 720px) {
  .pp-header { padding: 14px 16px 6px; }
  .pp-collapse-btn { top: calc(12px + env(safe-area-inset-top)); right: 16px; width: 40px; height: 40px; }
  .pp-title { font-size: 16px; }
  .pp-artist { font-size: 12px; }
  .pp-lyrics { padding: 0 12px; }
  .pp-line { font-size: 14px; }
  .pp-line.active { font-size: 18px; }
  .pp-controls { padding: 12px 16px calc(18px + env(safe-area-inset-bottom)); gap: 10px; }
  .pp-progress-row { grid-template-columns: 36px 1fr 36px; gap: 8px; }
  .pp-center-btns { gap: 16px; }
  .pp-play-btn { width: 56px; height: 56px; }
  .pp-skip-btn { width: 40px; height: 40px; }
  .pp-mode-btn { width: 36px; height: 36px; }
  .pp-volume { display: none; }
}
</style>
