<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDrawer, ElEmpty, ElIcon } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { parseLrc, fetchLyricsCached } from '../utils/lrc'

const props = defineProps<{ trackId: string; trackName: string }>()
const open = defineModel<boolean>('open', { default: false })

const loading = ref(false)
const rawLyrics = ref('')
const error = ref('')

const parsed = computed(() => parseLrc(rawLyrics.value))
const hasTimedLyrics = computed(() => parsed.value.length > 0)

async function fetchLyrics() {
  if (!props.trackId) return
  loading.value = true
  error.value = ''
  rawLyrics.value = ''
  const { raw, error: err } = await fetchLyricsCached(props.trackId)
  rawLyrics.value = raw
  error.value = err
  loading.value = false
}

watch(open, (v) => { if (v) fetchLyrics() })
</script>

<template>
  <el-drawer
    v-model="open"
    direction="btt"
    size="60vh"
    :with-header="false"
    class="lyrics-drawer"
  >
    <div class="lyrics-body">
      <header class="lyrics-head">
        <el-icon :size="18"><Document /></el-icon>
        <h3>{{ trackName }}</h3>
      </header>

      <div v-if="loading" class="lyrics-loading">
        <div v-for="n in 5" :key="n" class="lyric-skel" />
      </div>

      <div v-else-if="error" class="lyrics-error">
        <el-empty :description="error" :image-size="60" />
      </div>

      <div v-else-if="hasTimedLyrics" class="lyrics-lines">
        <p v-for="(line, i) in parsed" :key="i" class="lyric-line">
          {{ line.text || '···' }}
        </p>
      </div>

      <pre v-else-if="rawLyrics" class="lyrics-raw">{{ rawLyrics }}</pre>

      <el-empty v-else description="暂无歌词" :image-size="60" />
    </div>
  </el-drawer>
</template>

<style scoped>
.lyrics-body {
  padding: 20px 24px;
  color: var(--jn-ink);
  height: 100%;
  overflow-y: auto;
  scrollbar-width: none;
}
.lyrics-body::-webkit-scrollbar { display: none; }

.lyrics-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  color: var(--jn-ink-dim);
}
.lyrics-head h3 {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-size: 18px;
  font-weight: 500;
  color: var(--jn-ink-strong);
}

.lyrics-loading { display: flex; flex-direction: column; gap: 12px; }
.lyric-skel {
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
@keyframes skel { 0% { background-position: 200% 0 } 100% { background-position: -200% 0 } }

.lyrics-lines {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
}
.lyric-line {
  margin: 0;
  padding: 4px 12px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--jn-ink-dim);
  text-align: center;
  border-radius: 6px;
  transition: color 0.2s, background 0.2s;
}

.lyrics-raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 13px;
  line-height: 1.8;
  color: var(--jn-ink-dim);
}

.lyrics-error :deep(.el-empty__description p) {
  color: var(--jn-ink-muted);
}

.lyrics-drawer :deep(.el-drawer) {
  background: var(--jn-bg-elev) !important;
  border-top: 1px solid var(--jn-hair);
  border-radius: 16px 16px 0 0;
}
.lyrics-drawer :deep(.el-drawer__body) { padding: 0; }
</style>
