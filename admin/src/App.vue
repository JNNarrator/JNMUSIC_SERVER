<script setup lang="ts">
import { computed, watch } from 'vue'
import { ElButton, ElIcon, ElTooltip } from 'element-plus'
import { Sunny, Moon } from '@element-plus/icons-vue'
import TrackList from './components/TrackList.vue'
import PlayerBar from './components/PlayerBar.vue'
import BrandLogo from './components/BrandLogo.vue'
import LanzouAuthPanel from './components/LanzouAuthPanel.vue'
import { useThemeStore } from './stores/theme'
import { usePlayerStore } from './stores/player'

const theme = useThemeStore()
const player = usePlayerStore()

const BASE_TITLE = 'JNMusic · 夜猫电台'
watch(
  () => player.currentTrack,
  (t) => { document.title = t ? t.name + ' - ' + t.artist + ' | JNMusic' : BASE_TITLE },
  { immediate: true }
)
const themeLabel = computed(() => (theme.mode === 'dark' ? '切到白天模式' : '切到夜间模式'))
</script>

<template>
  <div class="shell">
    <div class="glow-orange" aria-hidden="true" />
    <div class="grain" aria-hidden="true" />

    <header class="topbar">
      <div class="brand">
        <BrandLogo :size="38" />
        <div class="brand-text">
          <p class="brand-name">JNMusic</p>
          <p class="brand-tag">深夜电台 · 一场没有主播的私人节目</p>
        </div>
      </div>
      <div class="marquee" aria-hidden="true">
        <span>NOW ON AIR · SIDE B · 33 ⅓ RPM · KEEP THE NEEDLE STEADY · </span>
        <span>NOW ON AIR · SIDE B · 33 ⅓ RPM · KEEP THE NEEDLE STEADY · </span>
      </div>
      <div class="header-actions">
        <LanzouAuthPanel />
        <el-tooltip :content="themeLabel" placement="bottom" :hide-after="800">
          <el-button
            class="theme-toggle"
            circle
            text
            :aria-label="themeLabel"
            @click="theme.toggle()"
          >
            <el-icon :size="18">
              <Moon v-if="theme.mode === 'dark'" />
              <Sunny v-else />
            </el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </header>

    <main class="stage">
      <TrackList />
    </main>

    <PlayerBar />
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 32px 40px 200px;
  color: var(--jn-ink);
  overflow-x: hidden;
  isolation: isolate;
  transition: color 0.35s ease;
}

.glow-orange {
  position: fixed;
  top: -20vh;
  right: -10vw;
  width: 70vw;
  height: 70vw;
  max-width: 900px;
  max-height: 900px;
  background: radial-gradient(circle at center, var(--jn-glow), transparent 60%);
  z-index: -1;
  pointer-events: none;
  filter: blur(20px);
  transition: background 0.35s ease;
}

.grain {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: -1;
  opacity: var(--jn-grain-opacity);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 40px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-dot {
  font-size: 28px;
  color: var(--jn-accent);
  transform: translateY(-2px);
}

.brand-name {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-weight: 700;
  font-size: 22px;
  letter-spacing: 0;
  color: var(--jn-ink-strong);
}

.brand-tag {
  margin: 2px 0 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-muted);
}

.marquee {
  flex: 1;
  max-width: 420px;
  overflow: hidden;
  white-space: nowrap;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-muted);
  mask-image: linear-gradient(90deg, transparent, #000 15%, #000 85%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 15%, #000 85%, transparent);
}

.marquee span {
  display: inline-block;
  animation: scroll 40s linear infinite;
}

@keyframes scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-100%); }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.theme-toggle {
  color: var(--jn-ink-dim) !important;
  background: transparent !important;
  border: 1px solid var(--jn-hair) !important;
  width: 40px !important;
  height: 40px !important;
  transition: color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}
.theme-toggle:hover {
  color: var(--jn-accent) !important;
  border-color: var(--jn-accent) !important;
  transform: rotate(-12deg);
}

.stage {
  max-width: 1080px;
  margin: 0 auto;
}

@media (max-width: 720px) {
  .shell {
    display: flex;
    flex-direction: column;
    height: 100dvh;
    padding: 16px 16px 0;
    overflow: hidden;
  }
  .topbar { margin-bottom: 16px; gap: 12px; flex-shrink: 0; }
  .stage {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
  .marquee { display: none; }
  .brand-name { font-size: 18px; }
  .brand-tag { font-size: 10.5px; }
  .theme-toggle { width: 36px !important; height: 36px !important; }
}
</style>
