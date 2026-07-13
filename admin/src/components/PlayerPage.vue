<script setup lang="ts">
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { ElIcon } from 'element-plus'
import {
  VideoPlay, VideoPause, DArrowLeft, DArrowRight,
  Sort, Refresh, RefreshRight, Mute, Close, Loading,
} from '@element-plus/icons-vue'
import { usePlayerStore, type PlayMode } from '../stores/player'
import { useUiStore } from '../stores/ui'
import { parseLrc, findCurrentLine, getLineProgress, fetchLyricsCached } from '../utils/lrc'

const player = usePlayerStore()
const ui = useUiStore()

const rawLyrics = ref('')
const lyricLines = computed(() => parseLrc(rawLyrics.value))
const hasTimedLyrics = computed(() => lyricLines.value.length > 0)
const currentLineIdx = ref(-1)
const lineProgress = ref(0)
const lyricsLoading = ref(false)
const lyricsError = ref<string | null>(null)

const lyricsContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])
function setLineRef(el: HTMLElement | null, idx: number) {
  if (el) lineRefs.value[idx] = el
}

async function fetchLyrics(trackId: string) {
  if (!trackId) return
  lyricsLoading.value = true
  lyricsError.value = null
  try {
    const { raw, error } = await fetchLyricsCached(trackId)
    rawLyrics.value = raw
    if (error) lyricsError.value = error
  } catch { lyricsError.value = '歌词加载失败' }
  finally { lyricsLoading.value = false }
}

function retryFetchLyrics() {
  if (player.currentTrack?.trackId) fetchLyrics(player.currentTrack.trackId)
}

watch(() => player.currentTrack?.trackId, (id) => { if (id) fetchLyrics(id) }, { immediate: true })

function updateTitle() {
  const track = player.currentTrack
  if (!track) {
    document.title = 'JNMusic'
    return
  }
  const lines = lyricLines.value
  const idx = currentLineIdx.value
  const currentLyric = (idx >= 0 && idx < lines.length && lines[idx].text) ? lines[idx].text.trim() : ''
  if (currentLyric && player.isPlaying) {
    document.title = currentLyric + ' | ' + track.name + ' - ' + track.artist + ' | JNMusic'
  } else {
    document.title = track.name + ' - ' + track.artist + ' | JNMusic'
  }
}

watch(currentLineIdx, updateTitle)
watch(() => player.isPlaying, updateTitle)
watch(() => player.currentTrack, updateTitle, { immediate: true })

let rafId = 0
let lastProgressUpdate = 0
const PROGRESS_THROTTLE = 50

function syncLyrics() {
  const lines = lyricLines.value
  if (lines.length) {
    const now = performance.now()
    const idx = findCurrentLine(lines, player.currentTime)
    if (idx !== currentLineIdx.value) {
      currentLineIdx.value = idx
      lineProgress.value = getLineProgress(lines, idx, player.currentTime)
      lastProgressUpdate = now
      if (ui.showPlayerPage) {
        nextTick(() => {
          const container = lyricsContainer.value
          const el = lineRefs.value[idx]
          if (container && el) {
            const containerHeight = container.clientHeight
            const scrollTo = el.offsetTop - containerHeight / 2 + el.offsetHeight / 2
            container.scrollTo({ top: scrollTo, behavior: 'smooth' })
          }
        })
      }
    } else if (now - lastProgressUpdate > PROGRESS_THROTTLE) {
      lineProgress.value = getLineProgress(lines, idx, player.currentTime)
      lastProgressUpdate = now
    }
  }
  rafId = requestAnimationFrame(syncLyrics)
}

watch(() => player.isPlaying, (playing) => {
  if (playing) {
    rafId = requestAnimationFrame(syncLyrics)
  } else {
    cancelAnimationFrame(rafId)
  }
}, { immediate: true })

onUnmounted(() => cancelAnimationFrame(rafId))

function fmt(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const s = Math.floor(seconds)
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`
}

const progressPercent = computed(() => {
  if (!player.duration) return 0
  return (player.currentTime / player.duration) * 100
})
const volumePercent = computed(() => player.volume * 100)

const MODE_META: Record<PlayMode, { label: string; icon: any }> = {
  list: { label: '列表循环', icon: RefreshRight },
  one: { label: '单曲循环', icon: Refresh },
  shuffle: { label: '随机播放', icon: Sort },
}
const modeMeta = computed(() => MODE_META[player.mode])

// Progress
const ppProgressRef = ref<HTMLElement | null>(null)
const showPpTooltip = ref(false)
const ppTooltipTime = ref('')
const ppTooltipPosition = ref(0)
const isDragging = ref(false)

function onPpProgressClick(e: MouseEvent) {
  if (!player.currentTrack || !ppProgressRef.value) return
  const rect = ppProgressRef.value.getBoundingClientRect()
  const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  player.seek(percent * player.duration)
}

function onPpProgressMouseDown(e: MouseEvent) {
  isDragging.value = true
  onPpProgressClick(e)
  const onMouseMove = (e: MouseEvent) => { if (isDragging.value) onPpProgressClick(e) }
  const onMouseUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function onPpProgressHover(e: MouseEvent) {
  if (!player.currentTrack || !ppProgressRef.value) return
  const rect = ppProgressRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  ppTooltipTime.value = fmt(percent * player.duration)
  ppTooltipPosition.value = x
  showPpTooltip.value = true
}

function onPpProgressMove(e: MouseEvent) {
  if (!showPpTooltip.value || !ppProgressRef.value) return
  const rect = ppProgressRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  ppTooltipTime.value = fmt(percent * player.duration)
  ppTooltipPosition.value = x
}

function onPpProgressLeave() {
  showPpTooltip.value = false
}

// Volume
function onVolumeClick(e: MouseEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  player.setVolume(Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width)))
}

// Touch drag
const dragOffset = ref(0)
const dragging = ref(false)
let startY = 0
let startTime = 0

function onTouchStart(e: TouchEvent) {
  const c = lyricsContainer.value
  if (c && c.scrollTop > 5) return
  startY = e.touches[0].clientY
  startTime = Date.now()
  dragging.value = true
}

function onTouchMove(e: TouchEvent) {
  if (!dragging.value) return
  const dy = e.touches[0].clientY - startY
  if (dy > 0) dragOffset.value = dy
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

function handleClose() { ui.closePlayerPage() }
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
      <header class="pp-header">
        <div class="pp-track-info">
          <p class="pp-title">{{ player.currentTrack?.name || '未在播放' }}</p>
          <p class="pp-artist">{{ player.currentTrack?.artist || '—' }}</p>
        </div>
      </header>

      <div ref="lyricsContainer" class="pp-lyrics">
        <template v-if="lyricsLoading">
          <div class="pp-lyrics-status">
            <div v-for="n in 5" :key="n" class="pp-skel" />
          </div>
        </template>
        <template v-else-if="lyricsError">
          <div class="pp-lyrics-error">
            <p class="pp-error-text">{{ lyricsError }}</p>
            <button class="pp-retry-btn" @click="retryFetchLyrics">
              <el-icon><Refresh /></el-icon>
              <span>重试</span>
            </button>
          </div>
        </template>
        <template v-else-if="hasTimedLyrics">
          <div class="pp-lyrics-pad-top" />
          <p
            v-for="(line, i) in lyricLines" :key="i"
            :ref="(el) => setLineRef(el as HTMLElement, i)"
            class="pp-line"
            :class="{ active: i === currentLineIdx, past: i < currentLineIdx }"
            :style="i === currentLineIdx ? { '--p': lineProgress + '%' } : null"
          >
            {{ line.text || '···' }}
          </p>
          <div class="pp-lyrics-pad-bottom" />
        </template>
        <template v-else-if="rawLyrics">
          <pre class="pp-lyrics-raw">{{ rawLyrics }}</pre>
        </template>
        <template v-else>
          <div class="pp-lyrics-empty"><p class="pp-lyrics-empty-text">暂无歌词</p></div>
        </template>
      </div>

      <footer class="pp-controls">
        <div class="pp-progress-row" ref="ppProgressRef"
             @mousedown="onPpProgressMouseDown"
             @mouseenter="onPpProgressHover"
             @mousemove="onPpProgressMove"
             @mouseleave="onPpProgressLeave">
          <span class="pp-time">{{ fmt(player.currentTime) }}</span>
          <div class="pp-progress-bar">
            <div class="pp-progress-fill" :style="{ width: progressPercent + '%' }" />
            <div class="pp-progress-thumb" :style="{ left: progressPercent + '%' }" />
          </div>
          <span class="pp-time">{{ fmt(player.duration) }}</span>
          <div v-if="showPpTooltip" class="pp-progress-tooltip" :style="{ left: ppTooltipPosition + 'px' }">
            {{ ppTooltipTime }}
          </div>
        </div>
        <div class="pp-main-ctl">
          <div class="pp-side pp-side-left">
            <button class="pp-mode-btn" :class="{ active: player.mode !== 'list' }" @click="player.cyclePlayMode()">
              <el-icon :size="20"><component :is="modeMeta.icon" /></el-icon>
              <span v-if="player.mode === 'one'" class="pp-mode-badge">1</span>
            </button>
          </div>
          <div class="pp-center-btns">
            <button class="pp-skip-btn" :disabled="!player.queue.length" @click.stop="player.prev()">
              <el-icon :size="24"><DArrowLeft /></el-icon>
            </button>
            <button class="pp-play-btn" :class="{ playing: player.isPlaying, loading: player.loading }" :disabled="!player.currentTrack || player.loading" @click.stop="player.toggle()">
              <span class="pp-play-ring" />
              <el-icon v-if="player.loading" :size="28" class="pp-play-icon spin"><Loading /></el-icon>
              <el-icon v-else :size="28" class="pp-play-icon">
                <VideoPause v-if="player.isPlaying" /><VideoPlay v-else />
              </el-icon>
            </button>
            <button class="pp-skip-btn" :disabled="!player.queue.length" @click.stop="player.next(true)">
              <el-icon :size="24"><DArrowRight /></el-icon>
            </button>
          </div>
          <div class="pp-side pp-side-right">
            <div class="pp-volume" @click="onVolumeClick">
              <el-icon :size="18" class="pp-vol-icon"><Mute /></el-icon>
              <div class="pp-volume-bar">
                <div class="pp-volume-fill" :style="{ width: volumePercent + '%' }" />
              </div>
            </div>
          </div>
        </div>
      </footer>

      <button class="pp-collapse-btn" aria-label="收起" @click="handleClose">
        <el-icon :size="18"><Close /></el-icon>
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.player-page-enter-active,
.player-page-leave-active {
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.35s ease;
}
.player-page-enter-from,
.player-page-leave-to { transform: translateY(100%); opacity: 0; }

.player-page {
  position: fixed; inset: 0; z-index: 50;

  display: flex; flex-direction: column;
  background: var(--jn-bg); color: var(--jn-ink);
  overflow: hidden; will-change: transform;
}

.pp-header { display: flex; align-items: center; justify-content: center; padding: 20px 24px 8px; flex-shrink: 0; }
.pp-collapse-btn {
  position: fixed; top: calc(16px + env(safe-area-inset-top)); right: 24px; z-index: 51;
  width: 44px; height: 44px; border: 1.5px solid var(--jn-hair-strong); border-radius: 50%;
  background: var(--jn-bar-bg); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
  color: var(--jn-ink-dim); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: color 0.2s, border-color 0.2s, transform 0.15s, background 0.2s;
}
.pp-collapse-btn:hover { color: var(--jn-ink-strong); border-color: var(--jn-ink-dim); background: var(--jn-bg-elev); }
.pp-collapse-btn:active { transform: scale(0.9); }
.pp-track-info { flex: 1; min-width: 0; text-align: center; }
.pp-title { margin: 0; font-family: 'Fraunces', serif; font-weight: 500; font-size: 18px; color: var(--jn-ink-strong); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pp-artist { margin: 4px 0 0; font-size: 13px; color: var(--jn-ink-dim); font-family: 'IBM Plex Mono', monospace; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 歌词区 */
.pp-lyrics {
  flex: 1; min-height: 0; overflow-y: auto; scrollbar-width: none;
  display: flex; flex-direction: column; align-items: center;
  padding: 0 24px; scroll-behavior: smooth;
}
.pp-lyrics::-webkit-scrollbar { display: none; }
.pp-lyrics-pad-top, .pp-lyrics-pad-bottom { flex-shrink: 0; height: 40vh; }

.pp-line {
  margin: 0; padding: 8px 16px;
  font-size: 15px; line-height: 1.8;
  text-align: center; max-width: 100%; word-break: break-word;
  transition: opacity 0.4s ease, transform 0.4s ease, font-size 0.4s ease;
  color: var(--jn-ink-muted); opacity: 0.5; transform: scale(0.95);
}
.pp-line.past { opacity: 0.2; filter: blur(0.3px); transform: scale(0.92); }
.pp-line.active {
  font-size: 20px; font-weight: 600; opacity: 1; transform: scale(1.06);
  background: linear-gradient(
    90deg,
    var(--jn-accent) 0%,
    var(--jn-accent) var(--p, 0%),
    var(--jn-ink-muted) var(--p, 0%),
    var(--jn-ink-muted) 100%
  );
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  filter: drop-shadow(0 0 15px rgba(242, 177, 52, 0.25));
}

.pp-lyrics-status { display: flex; flex-direction: column; gap: 14px; padding: 40% 40px 0; width: 100%; }
.pp-skel {
  height: 16px; border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%; animation: skel 1.4s linear infinite;
}
@keyframes skel { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.pp-lyrics-error { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 20px; }
.pp-error-text { font-size: 15px; color: var(--jn-danger); font-family: 'IBM Plex Mono', monospace; }
.pp-retry-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border: 1px solid var(--jn-hair); border-radius: 8px;
  background: transparent; color: var(--jn-ink-dim); cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.pp-retry-btn:hover { border-color: var(--jn-accent); color: var(--jn-accent); }

.pp-lyrics-raw { margin: 20% 0 0; white-space: pre-wrap; word-break: break-all; font-family: 'IBM Plex Mono', monospace; font-size: 13px; line-height: 1.8; color: var(--jn-ink-dim); text-align: left; max-width: 500px; }
.pp-lyrics-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
.pp-lyrics-empty-text { font-size: 15px; color: var(--jn-ink-muted); font-family: 'IBM Plex Mono', monospace; }

/* 底部控制区 */
.pp-controls { flex-shrink: 0; padding: 16px 28px calc(24px + env(safe-area-inset-bottom, 34px)); display: flex; flex-direction: column; gap: 14px; align-items: center; background: linear-gradient(to top, rgba(16, 12, 17, 0.6) 0%, transparent 100%); }

.pp-progress-tooltip {
  position: absolute; top: -32px; transform: translateX(-50%);
  background: var(--jn-bg-elev); color: var(--jn-ink-strong);
  padding: 4px 8px; border-radius: 6px;
  font-family: 'IBM Plex Mono', monospace; font-size: 11px;
  white-space: nowrap; pointer-events: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3); z-index: 10;
}
.pp-progress-row { display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; gap: 14px; width: 100%; max-width: 520px; position: relative; }
.pp-time { font-family: 'IBM Plex Mono', monospace; font-size: 11px; color: var(--jn-ink-dim); text-align: center; letter-spacing: 0.03em; }

.pp-progress-bar {
  position: relative; height: 4px;
  background: var(--jn-slider-track); border-radius: 2px; cursor: pointer;
}
.pp-progress-fill {
  position: absolute; top: 0; left: 0; height: 100%;
  background: linear-gradient(90deg, var(--jn-accent), var(--jn-accent-strong));
  border-radius: 2px; transition: width 0.1s linear;
}
.pp-progress-thumb {
  position: absolute; top: 50%; transform: translate(-50%, -50%);
  width: 14px; height: 14px; background: var(--jn-slider-thumb-fill);
  border: 2px solid var(--jn-accent); border-radius: 50%;
  cursor: pointer; opacity: 0; transition: opacity 0.15s;
}
.pp-progress-row:hover .pp-progress-thumb { opacity: 1; }

.pp-main-ctl { display: flex; align-items: center; justify-content: center; width: 100%; max-width: 520px; gap: 0; }
.pp-side { flex: 1; display: flex; align-items: center; }
.pp-side-left { justify-content: flex-start; }
.pp-side-right { justify-content: flex-end; }

.pp-mode-btn { position: relative; width: 40px; height: 40px; border: none; border-radius: 50%; background: transparent; color: var(--jn-ink-dim); cursor: pointer; display: flex; align-items: center; justify-content: center; transition: color 0.2s, background 0.2s; }
.pp-mode-btn:hover { color: var(--jn-ink-strong); background: var(--jn-row-hover); }
.pp-mode-btn.active { color: var(--jn-accent); }
.pp-mode-badge { position: absolute; top: 3px; right: 3px; font-size: 8px; font-family: 'IBM Plex Mono', monospace; color: var(--jn-accent-ink); background: var(--jn-accent); border-radius: 4px; padding: 0 3px; line-height: 1.4; }

.pp-center-btns { display: flex; align-items: center; gap: 20px; flex-shrink: 0; }
.pp-skip-btn { width: 44px; height: 44px; border: none; border-radius: 50%; background: transparent; color: var(--jn-ink-dim); cursor: pointer; display: flex; align-items: center; justify-content: center; transition: color 0.2s, transform 0.15s, background 0.2s; }
.pp-skip-btn:hover:not(:disabled) { color: var(--jn-ink-strong); background: var(--jn-row-hover); transform: scale(1.06); }
.pp-skip-btn:active:not(:disabled) { transform: scale(0.95); }
.pp-skip-btn:disabled { opacity: 0.3; cursor: default; }

.pp-play-btn { 
  position: relative; width: 64px; height: 64px; border: none; border-radius: 50%; 
  background: var(--jn-accent); color: var(--jn-accent-ink); cursor: pointer; 
  display: flex; align-items: center; justify-content: center; 
  transition: transform 0.15s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.15s ease; 
  box-shadow: 0 8px 32px var(--jn-glow); 
}
.pp-play-btn:hover:not(:disabled) { transform: scale(1.1); box-shadow: 0 14px 44px var(--jn-glow), 0 0 0 4px var(--jn-accent-soft); }
.pp-play-btn:active:not(:disabled) { transform: scale(0.9); box-shadow: 0 4px 16px var(--jn-glow); }
.pp-play-btn:disabled { opacity: 0.4; cursor: default; }
.pp-play-btn.loading { cursor: wait; }
.pp-play-ring { position: absolute; inset: -4px; border-radius: 50%; border: 1.5px solid var(--jn-accent); opacity: 0; pointer-events: none; transition: opacity 0.3s; }
.pp-play-btn.loading .pp-play-ring { opacity: 0.35; animation: pp-ring-pulse 1.2s ease-in-out infinite; }
.pp-play-btn.playing .pp-play-ring { opacity: 0.35; animation: pp-ring-pulse 2.4s ease-in-out infinite; }
@keyframes pp-ring-pulse { 0%, 100% { transform: scale(1); opacity: 0.35; } 50% { transform: scale(1.12); opacity: 0.12; } }
.pp-play-icon { position: relative; z-index: 1; }

.pp-volume { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.pp-vol-icon { color: var(--jn-ink-dim); }
.pp-volume-bar {
  position: relative; width: 90px; height: 4px;
  background: var(--jn-slider-track); border-radius: 2px;
}
.pp-volume-fill {
  position: absolute; top: 0; left: 0; height: 100%;
  background: var(--jn-accent); border-radius: 2px;
}

@media (max-width: 720px) {
  .pp-header { padding: calc(14px + env(safe-area-inset-top, 0px)) 16px 6px; }
  .pp-collapse-btn { top: calc(12px + env(safe-area-inset-top)); right: 16px; width: 40px; height: 40px; }
  .pp-title { font-size: 16px; } .pp-artist { font-size: 12px; }
  .pp-lyrics { padding: 0 12px; }
  .pp-line { font-size: 14px; } .pp-line.active { font-size: 18px; }
  .pp-controls { padding: 12px 16px calc(18px + env(safe-area-inset-bottom, 34px)); gap: 10px; }
  .pp-progress-row { grid-template-columns: 36px 1fr 36px; gap: 8px; }
  .pp-center-btns { gap: 16px; }
  .pp-play-btn { width: 56px; height: 56px; }
  .pp-skip-btn { width: 40px; height: 40px; }
  .pp-mode-btn { width: 36px; height: 36px; }
  .pp-volume { display: none; }
}
</style>
