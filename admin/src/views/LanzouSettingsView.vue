<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdminStore } from '../stores/admin'

const admin = useAdminStore()
const activeTab = ref<'cookie' | 'password'>('cookie')
const cookie = ref('')
const username = ref('')
const password = ref('')
const loading = ref(false)
const status = ref<{ authenticated: boolean; uid?: string; reason?: string } | null>(null)

const emit = defineEmits<{
  navigate: [route: 'main' | 'lanzou-settings']
}>()

/** 检查后端是否返回未授权，若是则清除本地 token 并跳回登录页 */
function handleUnauthorized(payload: { success: boolean; error?: { code?: string; message?: string } }) {
  if (!payload.success && payload.error?.code === 'UNAUTHORIZED') {
    admin.logout()
    return true
  }
  return false
}

async function fetchStatus() {
  try {
    const response = await fetch('/music/api/v1/admin/lanzou/status', {
      headers: {
        'X-Admin-Token': admin.token,
        'X-Admin-User': admin.username
      }
    })
    const payload = await response.json()
    if (handleUnauthorized(payload)) return
    if (payload.success) {
      status.value = payload.data
    } else {
      status.value = { authenticated: false, reason: payload.error?.message || '获取状态失败' }
    }
  } catch (error) {
    console.error('获取状态失败:', error)
    status.value = { authenticated: false, reason: '网络错误' }
  }
}

async function updateCookie() {
  if (!cookie.value.trim()) {
    alert('请输入Cookie')
    return
  }
  loading.value = true
  try {
    const response = await fetch('/music/api/v1/admin/lanzou/cookie', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Token': admin.token,
        'X-Admin-User': admin.username
      },
      body: JSON.stringify({ cookie: cookie.value.trim() })
    })
    const payload = await response.json()
    if (handleUnauthorized(payload)) return
    if (payload.success) {
      status.value = { authenticated: true, uid: payload.data.uid }
      alert('Cookie更新成功')
    } else {
      alert(payload.error?.message || '更新失败')
    }
  } catch (error) {
    alert('网络错误')
  } finally {
    loading.value = false
  }
}

async function loginWithPassword() {
  if (!username.value.trim() || !password.value) {
    alert('请输入蓝奏云账号和密码')
    return
  }
  loading.value = true
  try {
    const response = await fetch('/music/api/v1/admin/lanzou/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Token': admin.token,
        'X-Admin-User': admin.username
      },
      body: JSON.stringify({ username: username.value.trim(), password: password.value })
    })
    const payload = await response.json()
    if (handleUnauthorized(payload)) return
    if (payload.success) {
      status.value = { authenticated: true, uid: payload.data.uid }
      alert('登录成功')
    } else {
      alert(payload.error?.message || '登录失败')
    }
  } catch (error) {
    alert('网络错误')
  } finally {
    loading.value = false
  }
}

function goBack() {
  emit('navigate', 'main')
}

onMounted(fetchStatus)
</script>

<template>
  <main class="settings-page">
    <header class="top-bar">
      <button class="ghost-button" @click="goBack">← 返回</button>
      <h1 class="title">蓝奏云设置</h1>
      <div class="status-indicator">
        <span v-if="status?.authenticated" class="status-connected">已连接</span>
        <span v-else class="status-disconnected">未连接</span>
      </div>
    </header>

    <section class="card">
      <div class="status-info" v-if="status">
        <p v-if="status.authenticated">
          ✅ 已连接蓝奏云账号 (UID: {{ status.uid }})
        </p>
        <p v-else>
          ❌ 未连接: {{ status.reason || '请设置Cookie或登录' }}
        </p>
      </div>

      <div class="tabs">
        <button
          :class="{ active: activeTab === 'cookie' }"
          @click="activeTab = 'cookie'"
        >
          Cookie方式
        </button>
        <button
          :class="{ active: activeTab === 'password' }"
          @click="activeTab = 'password'"
        >
          账号密码方式
        </button>
      </div>

      <div v-if="activeTab === 'cookie'" class="tab-content">
        <div class="form-group">
          <label for="cookie">粘贴Cookie:</label>
          <textarea
            id="cookie"
            v-model="cookie"
            placeholder="从浏览器开发者工具复制Cookie..."
            rows="4"
          ></textarea>
          <p class="help-text">
            从浏览器的开发者工具中复制蓝奏云的Cookie，格式如:
            <code>phpsession=xxx; ...</code>
          </p>
        </div>
        <div class="form-actions">
          <button class="primary" @click="updateCookie" :disabled="loading">
            {{ loading ? '更新中...' : '更新Cookie' }}
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'password'" class="tab-content">
        <div class="form-group">
          <label for="lanzou-username">蓝奏云账号:</label>
          <input
            id="lanzou-username"
            v-model="username"
            placeholder="请输入蓝奏云账号（邮箱或手机号）"
          />
        </div>
        <div class="form-group">
          <label for="lanzou-password">蓝奏云密码:</label>
          <input
            id="lanzou-password"
            v-model="password"
            type="password"
            placeholder="请输入蓝奏云密码"
          />
        </div>
        <div class="form-actions">
          <button class="primary" @click="loginWithPassword" :disabled="loading">
            {{ loading ? '登录中...' : '自动登录' }}
          </button>
        </div>
      </div>
    </section>

    <section class="card instructions">
      <h2>使用说明</h2>
      <ol>
        <li>在蓝奏云根目录上传音频文件（支持格式：mp3, flac, wav, aac, m4a, ogg, opus, ape）</li>
        <li>文件名建议使用格式：<strong>歌手 - 歌名.mp3</strong>，系统会自动解析</li>
        <li>设置Cookie或登录后，系统将自动从蓝奏云根目录获取音乐列表</li>
        <li>更新音乐列表后，刷新主页即可看到新的音乐</li>
      </ol>
    </section>
  </main>
</template>

<style scoped>
.settings-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.title {
  margin: 0;
  font-size: 24px;
  color: #522d6b;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-connected {
  color: #4CAF50;
  font-weight: bold;
}

.status-disconnected {
  color: #f44336;
  font-weight: bold;
}

.card {
  border-radius: 26px;
  padding: 24px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 20px 60px rgba(120, 90, 160, 0.18);
  backdrop-filter: blur(16px) saturate(140%);
  margin-bottom: 24px;
}

.status-info {
  padding: 16px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  margin-bottom: 20px;
  color: #3a2a45;
}

.tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.tabs button {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 18px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.8);
  color: #522d6b;
  transition: all 0.2s;
}

.tabs button.active {
  background: linear-gradient(135deg, #ff9a9e, #a18cd1);
  color: white;
}

.tabs button:hover:not(.active) {
  background: rgba(255, 255, 255, 0.9);
}

.tab-content {
  padding: 16px 0;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #522d6b;
  font-weight: 600;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 2px 6px rgba(90, 50, 110, 0.08);
  color: #3a2a45;
  font-size: 14px;
}

.form-group textarea {
  resize: vertical;
  min-height: 100px;
}

.help-text {
  margin-top: 8px;
  font-size: 12px;
  color: #7c5488;
}

.help-text code {
  background: rgba(160, 120, 200, 0.18);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

.primary {
  background: linear-gradient(135deg, #ff9a9e, #a18cd1);
  color: white;
  padding: 12px 24px;
  border: none;
  border-radius: 18px;
  cursor: pointer;
  font-weight: 600;
}

.primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.instructions {
  color: #3a2a45;
}

.instructions h2 {
  margin-top: 0;
  color: #522d6b;
}

.instructions ol {
  padding-left: 20px;
}

.instructions li {
  margin-bottom: 12px;
  line-height: 1.6;
}

.ghost-button {
  border: none;
  border-radius: 18px;
  padding: 12px 18px;
  color: #522d6b;
  background: rgba(255, 255, 255, 0.75);
  cursor: pointer;
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
</style>
