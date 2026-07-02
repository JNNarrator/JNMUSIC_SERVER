<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAdminStore } from '../stores/admin'

type Track = {
  trackId: string
  name: string
  artist: string
  album?: string
  coverUrl?: string
  duration?: number
  format?: string
  fileSize?: number
  trackNumber?: number
  hasLyric?: boolean
  lyricUrl?: string
}

const admin = useAdminStore()
const trackList = reactive<Track[]>([])
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
const loading = ref(false)
const form = reactive({
  visible: false,
  saving: false,
  mode: 'create',
  trackId: '',
  name: '',
  artist: '',
  album: '',
  duration: 0,
  format: '',
  fileSize: 0,
  trackNumber: 1,
  hasLyric: false,
  coverUrl: '',
  lyricUrl: ''
})

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

function openCreate() {
  Object.assign(form, {
    visible: true,
    saving: false,
    mode: 'create',
    trackId: '',
    name: '',
    artist: '',
    album: '',
    duration: 0,
    format: '',
    fileSize: 0,
    trackNumber: 1,
    hasLyric: false,
    coverUrl: '',
    lyricUrl: ''
  })
}

function openEdit(item: Track) {
  Object.assign(form, {
    visible: true,
    saving: false,
    mode: 'edit',
    trackId: item.trackId,
    name: item.name,
    artist: item.artist,
    album: item.album ?? '',
    duration: item.duration ?? 0,
    format: item.format ?? '',
    fileSize: item.fileSize ?? 0,
    trackNumber: item.trackNumber ?? 1,
    hasLyric: item.hasLyric ?? false,
    coverUrl: item.coverUrl ?? '',
    lyricUrl: item.lyricUrl ?? ''
  })
}

async function submitForm() {
  form.saving = true
  try {
    const response = await fetch('/music/api/v1/admin/tracks', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Token': admin.token,
        'X-Admin-User': admin.username
      },
      body: JSON.stringify({
        trackId: form.trackId,
        name: form.name,
        artist: form.artist,
        album: form.album,
        duration: Number(form.duration || 0),
        format: form.format || null,
        fileSize: Number(form.fileSize || 0) || null,
        trackNumber: Number(form.trackNumber || 0) || null,
        hasLyric: Boolean(form.hasLyric),
        coverUrl: form.coverUrl || null,
        lyricUrl: form.lyricUrl || null
      })
    })
    const payload = await response.json()
    if (!response.ok || !payload.success) {
      alert(payload?.error?.message || '保存失败')
      return
    }
    form.visible = false
    await fetchTracks()
  } finally {
    form.saving = false
  }
}

async function uploadFile(file: File, type: string) {
  const payload = new FormData()
  payload.append('file', file)
  payload.append('type', type)
  if (form.trackId) {
    payload.append('trackId', form.trackId)
  }
  const response = await fetch('/music/api/v1/admin/tracks/upload', {
    method: 'POST',
    headers: { 'X-Admin-Token': admin.token, 'X-Admin-User': admin.username },
    body: payload
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok || !data.success) {
    throw new Error(data?.error?.message || '上传失败')
  }
  return data.data
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

onMounted(fetchTracks)
</script>

<template>
  <main class="admin-page">
    <header class="top-bar">
      <div>
        <p class="title">🎧 JNMusic 管理后台</p>
        <p class="subtitle">轻量可爱，只保留最小管理能力</p>
      </div>
      <button class="ghost-button" @click="logout">退出</button>
    </header>

    <section class="toolbar">
      <input v-model="keyword" placeholder="搜索歌曲 / 歌手" @keyup.enter="page = 1; fetchTracks()" />
      <button @click="page = 1; fetchTracks()">搜索</button>
      <button class="primary" @click="openCreate">新增歌曲</button>
    </section>

    <section class="card">
      <table>
        <thead>
          <tr>
            <th>歌曲ID</th>
            <th>名称</th>
            <th>歌手</th>
            <th>专辑</th>
            <th>时长</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in trackList" :key="item.trackId">
            <td>{{ item.trackId }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.artist }}</td>
            <td>{{ item.album || '-' }}</td>
            <td>{{ item.duration }}s</td>
            <td>
              <button class="tiny" @click="openEdit(item)">编辑</button>
            </td>
          </tr>
          <tr v-if="!trackList.length">
            <td colspan="6" class="empty">暂无数据</td>
          </tr>
        </tbody>
      </table>
      <footer class="pagination">
        <button :disabled="page === 1" @click="previousPage">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button :disabled="page >= totalPages" @click="nextPage">下一页</button>
      </footer>
    </section>

    <div v-if="form.visible" class="overlay" @click.self="form.visible = false">
      <section class="sheet">
        <h2>{{ form.mode === 'create' ? '新增歌曲' : '编辑歌曲' }}</h2>
        <form @submit.prevent="submitForm">
          <label>
            <span>歌曲ID</span>
            <input v-model="form.trackId" :disabled="form.mode === 'edit'" />
          </label>
          <label>
            <span>歌曲名</span>
            <input v-model="form.name" required />
          </label>
          <label>
            <span>歌手</span>
            <input v-model="form.artist" required />
          </label>
          <label>
            <span>专辑</span>
            <input v-model="form.album" />
          </label>
          <label>
            <span>时长(秒)</span>
            <input v-model.number="form.duration" type="number" min="0" />
          </label>
          <label>
            <span>格式</span>
            <input v-model="form.format" />
          </label>
          <label>
            <span>文件大小</span>
            <input v-model.number="form.fileSize" type="number" min="0" />
          </label>
          <label>
            <span>曲目序号</span>
            <input v-model.number="form.trackNumber" type="number" min="1" />
          </label>
          <label class="checkbox">
            <input v-model="form.hasLyric" type="checkbox" />
            <span>有歌词</span>
          </label>
          <label>
            <span>封面地址</span>
            <input v-model="form.coverUrl" />
          </label>
          <label>
            <span>歌词地址</span>
            <input v-model="form.lyricUrl" />
          </label>

          <div class="uploader">
            <span class="uploader-title">快速上传</span>
            <div class="uploader-actions">
              <label class="file-label">
                <input type="file" @change="async (event) => {
                  const target = event.target as HTMLInputElement
                  const file = target.files?.[0]
                  if (file) {
                    await uploadFile(file, 'audio')
                    alert('音频已上传')
                  }
                }" />
                <span>上传音频</span>
              </label>
              <label class="file-label">
                <input type="file" accept="image/*" @change="async (event) => {
                  const target = event.target as HTMLInputElement
                  const file = target.files?.[0]
                  if (file) {
                    await uploadFile(file, 'cover')
                    alert('封面已上传')
                  }
                }" />
                <span>上传封面</span>
              </label>
              <label class="file-label">
                <input type="file" accept=".lrc,.txt" @change="async (event) => {
                  const target = event.target as HTMLInputElement
                  const file = target.files?.[0]
                  if (file) {
                    await uploadFile(file, 'lyric')
                    alert('歌词已上传')
                  }
                }" />
                <span>上传歌词</span>
              </label>
            </div>
          </div>

          <footer class="sheet-actions">
            <button type="button" class="ghost" @click="form.visible = false">取消</button>
            <button type="submit" :disabled="form.saving">{{ form.saving ? '保存中...' : '保存' }}</button>
          </footer>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.admin-page {
  padding: 28px 26px 80px;
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

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(70, 30, 90, 0.25);
  display: grid;
  place-items: center;
  padding: 24px;
  backdrop-filter: blur(10px);
}

.sheet {
  width: min(520px, 100%);
  max-height: 92vh;
  overflow-y: auto;
  border-radius: 28px;
  padding: 22px 22px 24px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 30px 80px rgba(100, 60, 120, 0.35);
}

.sheet h2 {
  margin: 0 0 18px;
  color: #522d6b;
}

label {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
  color: #663a7a;
  font-weight: 600;
}

.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}

input[type='checkbox'] {
  width: 20px;
  height: 20px;
}

.sheet input {
  border: none;
  border-radius: 18px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 2px 6px rgba(90, 50, 110, 0.08);
  color: #3a2a45;
}

.uploader {
  border-radius: 22px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.7);
  margin: 18px 0;
}

.uploader-title {
  color: #522d6b;
  font-weight: 700;
}

.uploader-actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.file-label {
  cursor: pointer;
  border-radius: 20px;
  padding: 12px 18px;
  background: linear-gradient(135deg, #ff9a9e, #a18cd1);
  color: white;
  box-shadow: 0 12px 25px rgba(160, 120, 200, 0.35);
}

.file-label input {
  display: none;
}

.sheet-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.primary {
  background: linear-gradient(135deg, #ff9a9e, #a18cd1);
  color: white;
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
}

.ghost {
  background: rgba(255, 255, 255, 0.72);
}

.ghost-button {
  border: none;
  border-radius: 18px;
  padding: 12px 18px;
  color: #522d6b;
  background: rgba(255, 255, 255, 0.75);
}

.tiny {
  padding: 10px 14px;
  font-size: 14px;
}
</style>
