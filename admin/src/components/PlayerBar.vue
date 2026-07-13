<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElIcon } from 'element-plus'
import {
  VideoPlay,
  VideoPause,
  DArrowLeft,
  DArrowRight,
  Sort,
  Refresh,
  RefreshRight,
  Mute,
  Loading,
} from '@element-plus/icons-vue'
import { usePlayerStore, type PlayMode } from '../stores/player'
import { useUiStore } from '../stores/ui'

const player = usePlayerStore()
const ui = useUiStore()

function fmt(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const s = Math.floor(seconds)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${r.toString().padStart(2, '0')}`
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

// Tooltips
const showPrevTooltip = ref(false)
const showNextTooltip = ref(false)
const showModeTooltip = ref(false)

// Progress drag
const isDragging = ref(false)
const progressRef = ref<HTMLElement | null>(null)

function onProgressClick(e: MouseEvent) {
  if (!player.currentTrack || !progressRef.value) return
  const rect = progressRef.value.getBoundingClientRect()
  const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  player.seek(percent * player.duration)
}

function onProgressMouseDown(e: MouseEvent) {
  isDragging.value = true
  onProgressClick(e)
  
  const onMouseMove = (e: MouseEvent) => {
    if (isDragging.value) onProgressClick(e)
  }
  const onMouseUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// Progress tooltip
const showProgressTooltip = ref(false)
const tooltipTime = ref('')
const tooltipPosition = ref(0)

function onProgressHover(e: MouseEvent) {
  if (!player.currentTrack || !progressRef.value) return
  const rect = progressRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  tooltipTime.value = fmt(percent * player.duration)
  tooltipPosition.value = x
  showProgressTooltip.value = true
}

function onProgressMove(e: MouseEvent) {
  if (!showProgressTooltip.value) return
  const rect = progressRef.value!.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  tooltipTime.value = fmt(percent * player.duration)
  tooltipPosition.value = x
}

function onProgressLeave() {
  showProgressTooltip.value = false
}

function onBarClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.closest('.ctl-btn') || target.closest('.progress') || target.closest('.volume')) return
  ui.openPlayerPage()
}

function onCapsuleClick() {
  ui.openPlayerPage()
}
</script>

<template>
  <!-- 胶囊模式 -->
  <Transition name="capsule">
    <div
      v-if="ui.showPlayerPage && player.currentTrack"
      class="capsule"
      :class="{ spinning: player.isPlaying }"
      role="button"
      aria-label="展开播放器"
      @click="onCapsuleClick"
    >
      <div class="capsule-disc">
        <div class="capsule-disc-inner" />
        <div class="capsule-disc-hole" />
      </div>
    </div>
  </Transition>

  <!-- PlayerBar -->
  <footer
    v-show="!ui.showPlayerPage"
    class="player-bar"
    :class="{ empty: !player.currentTrack }"
    role="region"
    aria-label="播放器"
    @click="onBarClick"
  >
    <div class="cover" :class="{ spinning: player.isPlaying }">
      <div class="disc">
        <div class="disc-inner" />
        <div class="disc-hole" />
      </div>
    </div>

    <div class="info">
      <p class="title" :title="player.currentTrack?.name">
        {{ player.currentTrack?.name || '选一首歌，让唱针落下' }}
      </p>
      <p class="artist" :title="player.currentTrack?.artist">
        {{ player.currentTrack?.artist || '—' }}
      </p>
    </div>

    <div class="controls">
      <div class="btn-row">
        <div class="tooltip-wrapper"
             @mouseenter="showPrevTooltip = true"
             @mouseleave="showPrevTooltip = false">
          <button
            class="ctl-btn"
            aria-label="上一曲"
            :disabled="!player.queue.length"
            @click.stop="player.prev"
          >
            <el-icon :size="18"><DArrowLeft /></el-icon>
          </button>
          <div v-if="showPrevTooltip" class="tooltip">上一曲</div>
        </div>

        <button
          class="ctl-btn primary"
          :class="{ loading: player.loading }"
          :disabled="!player.currentTrack"
          aria-label="播放或暂停"
          @click.stop="player.toggle"
        >
          <el-icon v-if="player.loading" :size="20" class="spin"><Loading /></el-icon>
          <el-icon v-else :size="20">
            <VideoPause v-if="player.isPlaying" />
            <VideoPlay v-else />
          </el-icon>
        </button>

        <div class="tooltip-wrapper"
             @mouseenter="showNextTooltip = true"
             @mouseleave="showNextTooltip = false">
          <button
            class="ctl-btn"
            aria-label="下一曲"
            :disabled="!player.queue.length"
            @click.stop="player.next(true)"
          >
            <el-icon :size="18"><DArrowRight /></el-icon>
          </button>
          <div v-if="showNextTooltip" class="tooltip">下一曲</div>
        </div>

        <div class="tooltip-wrapper"
             @mouseenter="showModeTooltip = true"
             @mouseleave="showModeTooltip = false">
          <button
            class="ctl-btn mode-btn"
            :class="{ active: true, ['mode-' + player.mode]: true }"
            :aria-label="'播放模式：' + modeMeta.label"
            @click.stop="player.cyclePlayMode"
          >
            <el-icon :size="16"><component :is="modeMeta.icon" /></el-icon>
            <span v-if="player.mode === 'one'" class="badge">1</span>
          </button>
          <div v-if="showModeTooltip" class="tooltip">{{ modeMeta.label }}</div>
        </div>
      </div>

      <div class="progress"
           ref="progressRef"
           @click.stop
           @mousedown="onProgressMouseDown"
           @mouseenter="onProgressHover"
           @mousemove="onProgressMove"
           @mouseleave="onProgressLeave">
        <span class="time">{{ fmt(player.currentTime) }}</span>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }" />
          <div class="progress-thumb" :style="{ left: progressPercent + '%' }" />
        </div>
        <span class="time">{{ fmt(player.duration) }}</span>
        <div v-if="showProgressTooltip" class="progress-tooltip" :style="{ left: tooltipPosition + 'px' }">
          {{ tooltipTime }}
        </div>
      </div>
    </div>

    <div class="volume" @click.stop>
      <el-icon :size="16" class="vol-icon"><Mute /></el-icon>
      <div class="volume-bar"
           @mousedown="(e: MouseEvent) => {
             const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
             player.setVolume(Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width)))
           }">
        <div class="volume-fill" :style="{ width: volumePercent + '%' }" />
      </div>
    </div>
  </footer>
</template>

<style scoped>
/* 胶囊 */
.capsule {
  position: fixed;
  bottom: calc(20px + env(safe-area-inset-bottom, 0px));
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  cursor: pointer;
  z-index: 41;
  filter: drop-shadow(0 8px 24px rgba(0,0,0,0.4));
  transition: transform 0.2s ease;
}
.capsule:hover { transform: scale(1.08); }
.capsule:active { transform: scale(0.95); }

.capsule-disc {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 50%, var(--jn-cover-center) 0 30%, transparent 31%),
    repeating-radial-gradient(circle at 50% 50%, var(--jn-cover-groove) 0 2px, transparent 2px 4px),
    var(--jn-cover-outer);
}
.capsule-disc-inner,
.capsule-disc-hole { position: absolute; inset: 0; }
.capsule-disc-inner {
  margin: 22%;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--jn-accent), var(--jn-accent-strong));
}
.capsule-disc-hole {
  margin: 45%;
  border-radius: 50%;
  background: var(--jn-cover-hole);
}
.capsule.spinning .capsule-disc { animation: spin 8s linear infinite; }

/* PlayerBar */
.player-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  display: grid;
  grid-template-columns: 64px minmax(180px, 260px) 1fr 200px;
  align-items: center;
  gap: 20px;
  padding: 14px 24px 14px;
  padding-bottom: calc(14px + env(safe-area-inset-bottom, 0px));
  background: var(--jn-bar-bg);
  border-top: 1px solid var(--jn-hair);
  backdrop-filter: blur(18px) saturate(120%);
  -webkit-backdrop-filter: blur(18px) saturate(120%);
  transition: background 0.35s ease, border-color 0.35s ease;
  cursor: pointer;
}

.player-bar.empty .info .title { color: var(--jn-ink-muted); }

.cover {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 50%, var(--jn-cover-center) 0 30%, transparent 31%),
    repeating-radial-gradient(circle at 50% 50%, var(--jn-cover-groove) 0 2px, transparent 2px 4px),
    var(--jn-cover-outer);
  flex-shrink: 0;
  transition: background 0.35s ease;
}

.cover .disc,
.cover .disc-inner,
.cover .disc-hole { position: absolute; inset: 0; }
.cover .disc-inner {
  margin: 22%;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--jn-accent), var(--jn-accent-strong));
}
.cover .disc-hole {
  margin: 45%;
  border-radius: 50%;
  background: var(--jn-cover-hole);
}

.cover.spinning { animation: spin 8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.info { min-width: 0; }
.info .title {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-weight: 500;
  font-size: 16px;
  color: var(--jn-ink-strong);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.info .artist {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--jn-ink-dim);
  font-family: 'IBM Plex Mono', monospace;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.controls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.btn-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tooltip-wrapper { position: relative; }

.tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 8px;
  background: var(--jn-tooltip-bg);
  color: var(--jn-ink);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  pointer-events: none;
}

.ctl-btn {
  color: var(--jn-ink-dim);
  background: transparent;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 0.15s ease, transform 0.15s ease;
}
.ctl-btn:hover:not(:disabled) { color: var(--jn-ink-strong); }
.ctl-btn:active:not(:disabled) { transform: scale(0.92); }
.ctl-btn:disabled { opacity: 0.3; cursor: default; }

.mode-btn.active { color: var(--jn-accent); }
.mode-btn .badge {
  position: absolute;
  top: 4px; right: 4px;
  font-size: 8px;
  font-family: 'IBM Plex Mono', monospace;
  color: var(--jn-accent-ink);
  background: var(--jn-accent);
  border-radius: 4px;
  padding: 0 3px;
  line-height: 1.4;
}

.ctl-btn.primary {
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  width: 44px;
  height: 44px;
  box-shadow: 0 10px 24px var(--jn-glow);
  transition: transform 0.15s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.15s ease;
}
.ctl-btn.primary:hover:not(:disabled) {
  transform: scale(1.08);
  box-shadow: 0 14px 32px var(--jn-glow);
}
.ctl-btn.primary:active:not(:disabled) {
  transform: scale(0.92);
  box-shadow: 0 6px 16px var(--jn-glow);
}
.ctl-btn.primary.loading {
  animation: none;
  cursor: wait;
}
.spin { animation: spin 0.8s linear infinite; }

.progress {
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 520px;
  position: relative;
}
.progress .time {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  text-align: center;
}

.progress-bar {
  position: relative;
  height: 4px;
  background: var(--jn-slider-track);
  border-radius: 2px;
  cursor: pointer;
}
.progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, var(--jn-accent), var(--jn-accent-strong));
  border-radius: 2px;
  transition: width 0.1s linear;
}
.progress-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 12px;
  height: 12px;
  background: var(--jn-slider-thumb-fill);
  border: 2px solid var(--jn-accent);
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}
.progress:hover .progress-thumb { opacity: 1; }

.progress-tooltip {
  position: absolute;
  top: -32px;
  transform: translateX(-50%);
  background: var(--jn-bg-elev);
  color: var(--jn-ink-strong);
  padding: 4px 8px;
  border-radius: 6px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
  z-index: 10;
}

.volume {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}
.vol-icon { color: var(--jn-ink-dim); }

.volume-bar {
  position: relative;
  width: 120px;
  height: 4px;
  background: var(--jn-slider-track);
  border-radius: 2px;
  cursor: pointer;
}
.volume-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: var(--jn-accent);
  border-radius: 2px;
}

/* 移动端 */
@media (max-width: 720px) {
  .player-bar {
    flex-shrink: 0;
    position: relative;
    grid-template-columns: 44px 1fr auto;
    grid-template-areas:
      "cover info actions"
      "progress progress progress";
    gap: 10px 12px;
    padding: 10px 14px 10px;
    padding-bottom: calc(10px + env(safe-area-inset-bottom, 34px));
    box-sizing: border-box;
  }
  /* 填充 iOS 底部不可布局的孤岛间隙 */
  .player-bar::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: calc(env(safe-area-inset-bottom, 34px) + 20px);
    background: inherit;
    transform: translateY(100%);
    pointer-events: none;
  }
  .cover { grid-area: cover; width: 44px; height: 44px; }
  .info { grid-area: info; }
  .info .title { font-size: 14.5px; }
  .info .artist { font-size: 11.5px; }
  .controls { grid-area: actions; display: contents; }
  .btn-row { grid-area: actions; gap: 2px; }
  .progress {
    grid-area: progress;
    grid-template-columns: 36px 1fr 36px;
    gap: 8px;
    max-width: none;
  }
  .ctl-btn { width: 34px; height: 34px; }
  .ctl-btn.primary { width: 40px; height: 40px; }
  .volume { display: none; }
}
</style>
