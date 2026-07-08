<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAdminStore } from '../stores/admin'

type Track = {
  trackId: string
  name: string
  artist: string
  format?: string
  fileSize?: number
}

const admin = useAdminStore()
const trackList = reactive<Track[]>([])
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
const loading = ref(false)

const emit = defineEmits<{
  navigate: [route: 'main' | 'lanzou-settings']
}>()

async function fetchTracks() {
  loading.value = true
  try {
    const search = keyword.value.trim()
    const url = search
      ? `/music/api/v1/tracks/search?q=${encodeURIComponent(search)}&page=${page.value}&pageSize=10`
      : `/music/api/v1/tracks?page=${page.value}&pageSize=10`
    const response = await fetch(url)
    const payload = await response.json()
    if (!payload.success) {
      alert(payload.error?.message || '加载失败')
      return
    }
    trackList.splice(0, trackList.length, ...(payload.data.items as Track[]))
    total.value = payload.data.total
  } finally {
    loading.value = false
  }
}

function go(pageNumber: number) {
  if (pageNumber < 1 || pageNumber > totalPages.value) return
  page.value = pageNumber
  fetchTracks()
}

function logout() {
  admin.logout()
}

function previousPage() {
  go(page.value - 1)
}

function nextPage() {
  go(page.value + 1)
}

function openLanzouSettings() {
  emit('navigate', 'lanzou-settings')
}

onMounted(fetchTracks)
</script>

<template>
  <main class="admin-page">
    <header class="top-bar">
      <div>
        <p class="title">🎧 JNMusic 管理后台</p>
        <p class="subtitle">轻量可爱，只保留最小管理能力</p>
      </div>
      <div class="header-actions">
        <button class="ghost-button" @click="openLanzouSettings">蓝奏云设置</button>
        <button class="ghost-button" @click="logout">退出</button>
      </div>
    </header>

    <section class="toolbar">
      <input v-model="keyword" placeholder="搜索歌曲 / 歌手" @keyup.enter="page = 1; fetchTracks()" />
      <button @click="page = 1; fetchTracks()">搜索</button>
    </section>

    <section class="card">
      <table>
        <thead>
          <tr>
            <th>歌曲ID</th>
            <th>名称</th>
            <th>歌手</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in trackList" :key="item.trackId">
            <td>{{ item.trackId }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.artist }}</td>
          </tr>
          <tr v-if="!trackList.length">
            <td colspan="3" class="empty">暂无数据</td>
          </tr>
        </tbody>
      </table>
      <footer class="pagination">
        <button :disabled="page === 1" @click="previousPage">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button :disabled="page >= totalPages" @click="nextPage">下一页</button>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.admin-page {
  max-width: 1100px;
  margin: 0 auto;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.title {
  margin: 0;
  font-size: 26px;
  color: #522d6b;
}

.subtitle {
  margin: 6px 0 0;
  color: #7c5488;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.toolbar {
  display: flex;
  gap: 14px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.toolbar input {
  flex: 1 1 220px;
  border: none;
  border-radius: 20px;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: inset 0 2px 6px rgba(100, 60, 120, 0.08);
  color: #3a2a45;
}

.card {
  border-radius: 26px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 20px 60px rgba(120, 90, 160, 0.18);
  backdrop-filter: blur(16px) saturate(140%);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th {
  text-align: left;
  padding: 14px 12px;
  color: #7c5488;
}

td {
  padding: 14px 12px;
  color: #3a2a45;
  border-top: 1px solid rgba(160, 120, 200, 0.18);
}

.empty {
  text-align: center;
  color: #7c5488;
  padding: 30px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 22px;
  color: #522d6b;
}

button {
  border: none;
  border-radius: 18px;
  padding: 12px 18px;
  cursor: pointer;
  color: #522d6b;
  background: rgba(255, 255, 255, 0.8);
  transition: transform .15s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
}

button:disabled {
  opacity: 0.8;
  cursor: not-allowed;
}

.ghost-button {
  border: none;
  border-radius: 18px;
  padding: 12px 18px;
  color: #522d6b;
  background: rgba(255, 255, 255, 0.75);
}

@media (max-width: 680px) {
  .track-form {
    grid-template-columns: 1fr;
  }
}
</style>
