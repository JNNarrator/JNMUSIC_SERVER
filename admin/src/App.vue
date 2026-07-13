<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElIcon } from 'element-plus'
import { Sunny, Moon } from '@element-plus/icons-vue'
import TrackList from './components/TrackList.vue'
import PlayerBar from './components/PlayerBar.vue'
import PlayerPage from './components/PlayerPage.vue'
import BrandLogo from './components/BrandLogo.vue'
import LanzouAuthPanel from './components/LanzouAuthPanel.vue'
import { useThemeStore } from './stores/theme'
import { usePlayerStore } from './stores/player'

const theme = useThemeStore()
const player = usePlayerStore()

// 禁止选中、复制、粘贴
if (typeof window !== 'undefined') {
  document.addEventListener('selectstart', (e) => e.preventDefault())
  document.addEventListener('copy', (e) => e.preventDefault())
  document.addEventListener('cut', (e) => e.preventDefault())
  document.addEventListener('paste', (e) => e.preventDefault())
  document.addEventListener('contextmenu', (e) => e.preventDefault())
}

const BASE_TITLE = 'JNMusic · 夜猫电台'

// watch for title updates in PlayerPage

const themeLabel = computed(() => (theme.mode === 'dark' ? '切到白天模式' : '切到夜间模式'))
const showThemeTooltip = ref(false)
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
        <div class="theme-tooltip-wrapper"
             @mouseenter="showThemeTooltip = true"
             @mouseleave="showThemeTooltip = false">
          <button
            class="theme-toggle"
            :aria-label="themeLabel"
            @click="theme.toggle()"
          >
            <el-icon :size="18">
              <Moon v-if="theme.mode === 'dark'" />
              <Sunny v-else />
            </el-icon>
          </button>
          <div v-if="showThemeTooltip" class="theme-tooltip">{{ themeLabel }}</div>
        </div>
      </div>
    </header>

    <main class="stage">
      <TrackList />
    </main>

    <PlayerBar />
    <PlayerPage />
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  min-height: 100vh;
  min-height: calc(var(--vh, 1vh) * 100);
  padding: 32px 40px 200px;
  padding: calc(32px + env(safe-area-inset-top, 0px)) calc(40px + env(safe-area-inset-right, 0px)) calc(200px + env(safe-area-inset-bottom, 0px)) calc(40px + env(safe-area-inset-left, 0px));
  color: var(--jn-ink);
  overflow-x: hidden;
  isolation: isolate;
  transition: color 0.35s ease;
}

.glow-orange {
  position: fixed;
  top: calc(-20vh + env(safe-area-inset-top, 0px));
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
  margin-bottom: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
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

.theme-tooltip-wrapper {
  position: relative;
}

.theme-toggle {
  color: var(--jn-ink-dim);
  background: transparent;
  border: 1px solid var(--jn-hair);
  border-radius: 50%;
  width: 40px;
  height: 40px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}
.theme-toggle:hover {
  color: var(--jn-accent);
  border-color: var(--jn-accent);
  transform: rotate(-12deg);
}

.theme-tooltip {
  position: absolute;
  bottom: -32px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--jn-tooltip-bg);
  color: var(--jn-ink);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);
}

.stage {
  max-width: 1080px;
  margin: 0 auto;
}

@media (max-width: 720px) {
  .shell {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    padding: calc(16px + env(safe-area-inset-top, 0px)) calc(16px + env(safe-area-inset-right, 0px)) 0 calc(16px + env(safe-area-inset-left, 0px));
    overflow: hidden;
  }
  .topbar { margin-bottom: 8px; gap: 12px; flex-shrink: 0; }
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
  .theme-toggle { width: 36px; height: 36px; }
}
</style>
