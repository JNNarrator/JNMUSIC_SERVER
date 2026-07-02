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

type UploadResult = {
  trackId: string
  type: 'audio' | 'cover' | 'lyric'
  fileName?: string
  format?: string
  fileSize?: number
  url?: string
}

type UploadType = UploadResult['type']

type UploadStatus = {
  uploading: boolean
  progress: number
  fileName: string
  message: string
  error: string
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
const uploadState = reactive<Record<UploadType, UploadStatus>>({
  audio: { uploading: false, progress: 0, fileName: '', message: '', error: '' },
  cover: { uploading: false, progress: 0, fileName: '', message: '', error: '' },
  lyric: { uploading: false, progress: 0, fileName: '', message: '', error: '' }
})
const isUploading = computed(() => Object.values(uploadState).some((item) => item.uploading))
const uploadItems = computed(() => [
  { type: 'audio' as const, label: '音频', ...uploadState.audio },
  { type: 'cover' as const, label: '封面', ...uploadState.cover },
  { type: 'lyric' as const, label: '歌词', ...uploadState.lyric }
])

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
  resetUploadState()
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
  resetUploadState()
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
  if (isUploading.value) {
    alert('文件还在上传中，请上传完成后再保存')
    return
  }
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
        trackId: form.trackId || null,
        name: form.name,
        artist: form.artist || null,
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

function uploadFile(file: File, type: UploadType, onProgress: (progress: number) => void) {
  const payload = new FormData()
  payload.append('file', file)
  payload.append('type', type)
  if (form.trackId) {
    payload.append('trackId', form.trackId)
  }
  return new Promise<UploadResult>((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('POST', '/music/api/v1/admin/tracks/upload')
    request.setRequestHeader('X-Admin-Token', admin.token)
    request.setRequestHeader('X-Admin-User', admin.username)
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)))
      }
    }
    request.onload = () => {
      const data = parseJsonResponse(request.responseText)
      if (request.status < 200 || request.status >= 300 || !data.success) {
        reject(new Error(data?.error?.message || '上传失败'))
        return
      }
      onProgress(100)
      resolve(data.data as UploadResult)
    }
    request.onerror = () => reject(new Error('网络异常，上传失败'))
    request.onabort = () => reject(new Error('上传已取消'))
    request.send(payload)
  })
}

function parseJsonResponse(value: string) {
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}

function resetUploadState() {
  Object.values(uploadState).forEach((item) => {
    item.uploading = false
    item.progress = 0
    item.fileName = ''
    item.message = ''
    item.error = ''
  })
}

async function handleUpload(event: Event, type: UploadType) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const state = uploadState[type]
  state.uploading = true
  state.progress = 1
  state.fileName = file.name
  state.message = '上传中...'
  state.error = ''

  if (type === 'audio') {
    parseFilename(file)
  }

  try {
    const result = await uploadFile(file, type, (progress) => {
      state.progress = progress
    })
    applyUploadResult(result)
    state.progress = 100
    state.message = `${uploadLabel(type)}已上传`
  } catch (error) {
    state.error = error instanceof Error ? error.message : '上传失败'
    state.message = ''
  } finally {
    state.uploading = false
    target.value = ''
  }
}

function uploadLabel(type: UploadType) {
  if (type === 'audio') return '音频'
  if (type === 'cover') return '封面'
  return '歌词'
}

function applyUploadResult(result: UploadResult) {
  if (result.trackId) {
    form.trackId = result.trackId
  }
  if (result.type === 'audio') {
    form.format = result.format ?? form.format
    form.fileSize = result.fileSize ?? form.fileSize
  } else if (result.type === 'cover') {
    form.coverUrl = result.url ?? form.coverUrl
  } else if (result.type === 'lyric') {
    form.lyricUrl = result.url ?? form.lyricUrl
    form.hasLyric = true
  }
}

async function deleteTrack(item: Track) {
  if (!confirm(`确定删除「${item.name}」？此操作不可恢复。`)) return
  const resp = await fetch(`/music/api/v1/admin/tracks/${item.trackId}`, {
    method: 'DELETE',
    headers: { 'X-Admin-Token': admin.token, 'X-Admin-User': admin.username }
  })
  const p = await resp.json()
  if (!resp.ok || !p.success) { alert(p.error?.message || '删除失败'); return }
  await fetchTracks()
}

function parseFilename(file: File) {
  const n = file.name.replace(/\.[^.]+$/, '');
  const s = n.indexOf(' - ');
  if (s > 0) { form.artist = n.substring(0, s).trim(); form.name = n.substring(s + 3).trim(); }
  else { form.name = n; }
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
              <button class="tiny danger" @click="deleteTrack(item)">删除</button>
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

    <div v-if="form.visible" class="overlay" @click.self="!isUploading && (form.visible = false)">
      <section class="sheet">
        <h2>{{ form.mode === 'create' ? '新增歌曲' : '编辑歌曲' }}</h2>
        <form class="track-form" @submit.prevent="submitForm">
          <div class="uploader">
            <span class="uploader-title">快速上传 - 自动识别歌手 / 歌名</span>
            <p class="uploader-hint">文件名格式为「歌手 - 歌名.mp3」会自动填入下方字段，专辑不填则默认为「未知」</p>
            <div class="uploader-actions">
              <label class="file-label">
                <input type="file" :disabled="isUploading" @change="handleUpload($event, 'audio')" />
                <span>上传音频</span>
              </label>
              <label class="file-label">
                <input type="file" accept="image/*" :disabled="isUploading" @change="handleUpload($event, 'cover')" />
                <span>上传封面</span>
              </label>
              <label class="file-label">
                <input type="file" accept=".lrc,.txt" :disabled="isUploading" @change="handleUpload($event, 'lyric')" />
                <span>上传歌词</span>
              </label>
            </div>
            <div class="upload-progress-list" aria-live="polite">
              <div
                v-for="item in uploadItems"
                :key="item.type"
                v-show="item.fileName || item.uploading || item.message || item.error"
                class="upload-progress-item"
                :class="{ failed: item.error }"
              >
                <div class="upload-progress-meta">
                  <span>{{ item.label }} · {{ item.fileName || '等待选择文件' }}</span>
                  <strong>{{ item.progress }}%</strong>
                </div>
                <div class="progress-track">
                  <span class="progress-fill" :style="{ width: `${item.progress}%` }"></span>
                </div>
                <p>{{ item.error || item.message }}</p>
              </div>
            </div>
          </div>
          <label v-if="form.mode === 'edit'">
            <span>歌曲ID</span>
            <input v-model="form.trackId" :disabled="form.mode === 'edit'" />
          </label>
          <label>
            <span>歌曲名</span>
            <input v-model="form.name" required />
          </label>
          <label>
            <span>歌手</span>
            <input v-model="form.artist" />
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


          <footer class="sheet-actions">
            <button type="button" class="ghost" :disabled="isUploading" @click="form.visible = false">取消</button>
            <button type="submit" :disabled="form.saving || isUploading">
              {{ isUploading ? '上传完成后可保存' : form.saving ? '保存中...' : '保存' }}
            </button>
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
  width: min(760px, 100%);
  max-height: 88vh;
  overflow-y: auto;
  border-radius: 22px;
  padding: 12px 14px 14px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 30px 80px rgba(100, 60, 120, 0.35);
}

.sheet h2 {
  margin: 0 0 8px;
  color: #522d6b;
}

.track-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 10px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 0;
  color: #663a7a;
  font-weight: 600;
}

.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

input[type='checkbox'] {
  width: 20px;
  height: 20px;
}

.sheet input {
  border: none;
  border-radius: 14px;
  padding: 7px 10px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 2px 6px rgba(90, 50, 110, 0.08);
  color: #3a2a45;
}

.uploader {
  grid-column: 1 / -1;
  border-radius: 16px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.7);
  margin: 0;
}

.uploader-hint {
  color: #886699;
  font-size: 13px;
  margin: 4px 0 0;
}

.uploader-title {
  color: #522d6b;
  font-weight: 700;
}

.uploader-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.file-label {
  cursor: pointer;
  border-radius: 16px;
  padding: 6px 12px;
  font-size: 13px;
  background: linear-gradient(135deg, #ff9a9e, #a18cd1);
  color: white;
  box-shadow: 0 8px 18px rgba(160, 120, 200, 0.25);
}

.file-label input {
  display: none;
}

.file-label:has(input:disabled) {
  cursor: wait;
  opacity: 0.72;
}

.upload-progress-list {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.upload-progress-item {
  border-radius: 14px;
  padding: 7px 9px;
  background: rgba(255, 246, 252, 0.86);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.upload-progress-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #663a7a;
  font-size: 12px;
}

.upload-progress-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-progress-meta strong {
  flex: 0 0 auto;
  color: #ff7a94;
}

.progress-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  margin-top: 5px;
  background: rgba(160, 140, 209, 0.18);
}

.progress-fill {
  display: block;
  height: 100%;
  min-width: 0;
  border-radius: inherit;
  background: linear-gradient(90deg, #8ee6c9, #ffd36e, #ff9a9e);
  transition: width .2s ease;
}

.upload-progress-item p {
  margin: 4px 0 0;
  color: #886699;
  font-size: 12px;
}

.upload-progress-item.failed .progress-fill {
  background: linear-gradient(90deg, #ff9a9e, #f5576c);
}

.upload-progress-item.failed p {
  color: #c84668;
}

.sheet-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
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
  cursor: not-allowed;
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

.danger {
  background: linear-gradient(135deg, #f093fb, #f5576c) !important;
  color: white !important;
}

@media (max-width: 680px) {
  .track-form {
    grid-template-columns: 1fr;
  }
}
</style>



