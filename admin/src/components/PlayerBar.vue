<script setup lang="ts">
import { computed } from 'vue'
import {
  VideoPlay,
  VideoPause,
  DArrowLeft,
  DArrowRight,
  Sort,
  Refresh,
  RefreshRight,
  Mute,
} from '@element-plus/icons-vue'
import { usePlayerStore, type PlayMode } from '../stores/player'

const player = usePlayerStore()

function fmt(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const s = Math.floor(seconds)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${r.toString().padStart(2, '0')}`
}

const progress = computed({
  get: () => player.currentTime,
  set: (v: number) => player.seek(v),
})

const volumeValue = computed({
  get: () => Math.round(player.volume * 100),
  set: (v: number) => player.setVolume(v / 100),
})

const MODE_META: Record<PlayMode, { label: string; icon: any }> = {
  list: { label: '列表循环', icon: RefreshRight },
  one: { label: '单曲循环', icon: Refresh },
  shuffle: { label: '随机播放', icon: Sort },
}

const modeMeta = computed(() => MODE_META[player.mode])
</script>

<template>
  <footer
    class="player-bar"
    :class="{ empty: !player.currentTrack }"
    role="region"
    aria-label="播放器"
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
        <el-tooltip content="上一曲" placement="top" :hide-after="800">
          <el-button
            circle
            text
            class="ctl-btn"
            :icon="DArrowLeft"
            aria-label="上一曲"
            :disabled="!player.queue.length"
            @click="player.prev"
          />
        </el-tooltip>
        <el-button
          circle
          class="ctl-btn primary"
          :icon="player.isPlaying ? VideoPause : VideoPlay"
          :loading="player.loading"
          :disabled="!player.currentTrack"
          aria-label="播放或暂停"
          @click="player.toggle"
        />
        <el-tooltip content="下一曲" placement="top" :hide-after="800">
          <el-button
            circle
            text
            class="ctl-btn"
            :icon="DArrowRight"
            aria-label="下一曲"
            :disabled="!player.queue.length"
            @click="player.next(true)"
          />
        </el-tooltip>
        <el-tooltip :content="modeMeta.label" placement="top" :hide-after="800">
          <el-button
            circle
            text
            class="ctl-btn mode-btn"
            :class="{ active: true, ['mode-' + player.mode]: true }"
            :icon="modeMeta.icon"
            :aria-label="'播放模式：' + modeMeta.label"
            @click="player.cyclePlayMode"
          >
            <span v-if="player.mode === 'one'" class="badge">1</span>
          </el-button>
        </el-tooltip>
      </div>

      <div class="progress">
        <span class="time">{{ fmt(player.currentTime) }}</span>
        <el-slider
          v-model="progress"
          class="progress-slider"
          :min="0"
          :max="Math.max(0.1, player.duration)"
          :step="0.1"
          :show-tooltip="false"
          :disabled="!player.currentTrack"
        />
        <span class="time">{{ fmt(player.duration) }}</span>
      </div>
    </div>

    <div class="volume">
      <el-icon :size="18" class="vol-icon" aria-hidden="true"><Mute /></el-icon>
      <el-slider
        v-model="volumeValue"
        class="volume-slider"
        :min="0"
        :max="100"
        :show-tooltip="false"
        aria-label="音量"
      />
    </div>
  </footer>
</template>

<style scoped>
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
  padding: 14px 24px calc(14px + env(safe-area-inset-bottom));
  background: var(--jn-bar-bg);
  border-top: 1px solid var(--jn-hair);
  backdrop-filter: blur(18px) saturate(120%);
  -webkit-backdrop-filter: blur(18px) saturate(120%);
  transition: background 0.35s ease, border-color 0.35s ease;
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

.ctl-btn {
  color: var(--jn-ink-dim) !important;
  background: transparent !important;
  border: none !important;
  width: 36px !important;
  height: 36px !important;
  transition: color 0.15s ease, transform 0.15s ease;
  position: relative;
}
.ctl-btn:hover { color: var(--jn-ink-strong) !important; }
.mode-btn.active { color: var(--jn-accent) !important; }
.ctl-btn.primary {
  background: var(--jn-accent) !important;
  color: var(--jn-accent-ink) !important;
  width: 44px !important;
  height: 44px !important;
  box-shadow: 0 10px 24px var(--jn-glow);
}
.ctl-btn.primary:hover { transform: scale(1.03); }
.ctl-btn .badge {
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

.progress {
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 520px;
}
.progress .time {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  text-align: center;
}
.progress-slider { width: 100%; }

.volume {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}
.vol-icon { color: var(--jn-ink-dim); }
.volume-slider { width: 120px; }

/* 移动端优化：折叠为两行紧凑布局，隐藏音量、时长文字。 */
@media (max-width: 720px) {
  .player-bar {
    grid-template-columns: 44px 1fr auto;
    grid-template-areas:
      "cover info actions"
      "progress progress progress";
    gap: 10px 12px;
    padding: 10px 14px calc(10px + env(safe-area-inset-bottom));
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
  .ctl-btn { width: 34px !important; height: 34px !important; }
  .ctl-btn.primary { width: 40px !important; height: 40px !important; }
  .volume { display: none; }
}
</style>
