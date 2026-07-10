<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { showToast } from 'vant'
import { ElIcon } from 'element-plus'
import { Cloudy, Key, Link, Refresh, Lock, Close } from '@element-plus/icons-vue'

type Status = { authenticated: boolean; uid?: string; reason?: string }

const open = ref(false)
const status = ref<Status | null>(null)
const loading = ref(false)
const submitting = ref(false)
const mode = ref<'cookie' | 'password'>('cookie')
const cookie = ref('')
const username = ref('')
const password = ref('')
const showPillTooltip = ref(false)
const showRefreshTooltip = ref(false)

const light = computed(() => {
  if (!status.value) return 'unknown'
  return status.value.authenticated ? 'ok' : 'off'
})

const lightLabel = computed(() => {
  if (loading.value) return '校验中'
  if (!status.value) return '未校验'
  if (status.value.authenticated) return '已连接 · uid ' + (status.value.uid || '--')
  return status.value.reason ? '离线 · ' + status.value.reason : '离线'
})

async function fetchStatus() {
  loading.value = true
  try {
    const res = await fetch('/music/api/v1/admin/lanzou/status')
    const p = await res.json()
    if (p.success) status.value = p.data
    else status.value = { authenticated: false, reason: p.error?.message || '获取状态失败' }
  } catch (e) {
    status.value = { authenticated: false, reason: '网络异常' }
  } finally {
    loading.value = false
  }
}

async function submitCookie() {
  if (!cookie.value.trim()) { showToast({ message: '请先粘贴 Cookie', type: 'warning' }); return }
  submitting.value = true
  try {
    const res = await fetch('/music/api/v1/admin/lanzou/cookie', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cookie: cookie.value.trim() }),
    })
    const p = await res.json()
    if (p.success) { status.value = p.data; cookie.value = ''; showToast({ message: 'Cookie 已生效', type: 'success' }) }
    else showToast({ message: p.error?.message || 'Cookie 无效', type: 'error' })
  } catch (e) { showToast({ message: '网络异常', type: 'error' }) } finally { submitting.value = false }
}

async function submitLogin() {
  if (!username.value.trim() || !password.value) { showToast({ message: '请填写账号与密码', type: 'warning' }); return }
  submitting.value = true
  try {
    const res = await fetch('/music/api/v1/admin/lanzou/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    })
    const p = await res.json()
    if (p.success) { status.value = p.data; password.value = ''; showToast({ message: '登录成功', type: 'success' }) }
    else showToast({ message: p.error?.message || '登录失败', type: 'error' })
  } catch (e) { showToast({ message: '网络异常', type: 'error' }) } finally { submitting.value = false }
}

function toggleOpen() { open.value = !open.value }
watch(open, (v) => { if (v) fetchStatus() })
fetchStatus()
</script>

<template>
  <div class="pill-tooltip-wrapper"
       @mouseenter="showPillTooltip = true"
       @mouseleave="showPillTooltip = false">
    <button
      class="lanzou-pill"
      :class="`state-${light}`"
      type="button"
      :aria-label="`蓝奏云认证：${lightLabel}`"
      @click="toggleOpen"
    >
      <span class="dot" aria-hidden="true" />
      <el-icon :size="15"><Cloudy /></el-icon>
      <span class="txt">蓝奏云</span>
    </button>
    <div v-if="showPillTooltip" class="pill-tooltip">{{ lightLabel }}</div>
  </div>

  <!-- Drawer -->
  <Transition name="drawer-slide">
    <div v-if="open" class="drawer-overlay" @click.self="open = false">
      <div class="drawer-panel">
        <div class="drawer-body">
          <button class="drawer-close" @click="open = false" aria-label="关闭">
            <el-icon :size="20"><Close /></el-icon>
          </button>
          <header class="head">
            <p class="eyebrow">// Signal Check</p>
            <h3>蓝奏云认证</h3>
            <p class="lead">连线蓝奏云网盘作为音频源。选一种方式接入，会立刻用 uid 探活。</p>
          </header>

          <section class="status-card">
            <div class="status-line">
              <span class="beacon" :class="`state-${light}`" />
              <span class="label">{{ lightLabel }}</span>
              <div class="tooltip-wrapper"
                   @mouseenter="showRefreshTooltip = true"
                   @mouseleave="showRefreshTooltip = false">
                <button class="refresh" :disabled="loading" @click="fetchStatus">
                  <el-icon :size="15" :class="{ spinning: loading }"><Refresh /></el-icon>
                </button>
                <div v-if="showRefreshTooltip" class="tooltip-sm">重新校验</div>
              </div>
            </div>
            <p v-if="status?.authenticated" class="uid">UID · {{ status.uid }}</p>
          </section>

          <!-- Mode switch -->
          <div class="mode-switch">
            <button
              class="mode-btn"
              :class="{ active: mode === 'cookie' }"
              @click="mode = 'cookie'"
            >
              <el-icon :size="14"><Link /></el-icon>
              Cookie
            </button>
            <button
              class="mode-btn"
              :class="{ active: mode === 'password' }"
              @click="mode = 'password'"
            >
              <el-icon :size="14"><Key /></el-icon>
              账号密码
            </button>
          </div>

          <!-- Cookie form -->
          <form v-if="mode === 'cookie'" class="form" @submit.prevent="submitCookie">
            <div class="field">
              <label class="field-label">Cookie</label>
              <textarea
                v-model="cookie"
                class="textarea"
                placeholder="粘贴浏览器中的蓝奏云 Cookie"
                rows="4"
              />
              <p class="hint">登录蓝奏云网页版，F12 → Network → 复制 Cookie</p>
            </div>
            <button class="submit" type="submit" :disabled="submitting || !cookie.trim()">
              {{ submitting ? '校验中…' : '校验并保存' }}
            </button>
          </form>

          <!-- Password form -->
          <form v-else class="form" @submit.prevent="submitLogin">
            <div class="field">
              <label class="field-label">账号</label>
              <div class="input-wrapper">
                <el-icon :size="14"><Link /></el-icon>
                <input
                  v-model="username"
                  class="input"
                  type="text"
                  placeholder="蓝奏云账号"
                  autocomplete="username"
                />
              </div>
            </div>
            <div class="field">
              <label class="field-label">密码</label>
              <div class="input-wrapper">
                <el-icon :size="14"><Lock /></el-icon>
                <input
                  v-model="password"
                  class="input"
                  type="password"
                  placeholder="蓝奏云密码"
                  autocomplete="current-password"
                />
              </div>
            </div>
            <button class="submit" type="submit" :disabled="submitting || !username.trim() || !password">
              {{ submitting ? '登录中…' : '登录' }}
            </button>
          </form>

          <p class="footnote">
            认证信息仅保存在本地浏览器，不会上传到任何服务器。
          </p>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* Pill */
.pill-tooltip-wrapper {
  position: relative;
  display: inline-flex;
}

.lanzou-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 14px 0 12px;
  border-radius: 999px;
  border: 1px solid var(--jn-hair);
  background: transparent;
  color: var(--jn-ink-dim);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11.5px;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease, transform 0.2s ease;
  max-width: 200px;
  overflow: hidden;
}
.lanzou-pill:hover {
  color: var(--jn-ink-strong);
  border-color: var(--jn-hair-strong);
  background: var(--jn-row-hover);
}
.lanzou-pill:active { transform: translateY(1px); }
.lanzou-pill .txt { 
  line-height: 1; 
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lanzou-pill.state-ok { color: var(--jn-accent); border-color: var(--jn-accent); }
.lanzou-pill.state-off { color: var(--jn-danger); border-color: color-mix(in oklab, var(--jn-danger) 55%, transparent); }

.pill-tooltip {
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
  pointer-events: none;
}

.dot {
  position: relative;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--jn-ink-muted);
  box-shadow: 0 0 0 3px transparent;
  transition: background 0.25s ease, box-shadow 0.25s ease;
  flex-shrink: 0;
}
.state-ok .dot {
  background: var(--jn-accent);
  box-shadow: 0 0 0 3px var(--jn-accent-soft);
  animation: pulse-ok 2.4s ease-in-out infinite;
}
.state-off .dot {
  background: var(--jn-danger);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--jn-danger) 22%, transparent);
}
@keyframes pulse-ok {
  0%, 100% { box-shadow: 0 0 0 3px var(--jn-accent-soft); }
  50%      { box-shadow: 0 0 0 6px color-mix(in oklab, var(--jn-accent) 12%, transparent); }
}

/* Drawer */
.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: opacity 0.3s ease;
}
.drawer-slide-enter-active .drawer-panel,
.drawer-slide-leave-active .drawer-panel {
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}
.drawer-slide-enter-from,
.drawer-slide-leave-to { opacity: 0; }
.drawer-slide-enter-from .drawer-panel,
.drawer-slide-leave-to .drawer-panel { transform: translateX(100%); }

.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 200;
  display: flex;
  justify-content: flex-end;
}

.drawer-panel {
  width: 380px;
  max-width: 90vw;
  height: 100%;
  background: var(--jn-bg-elev);
  box-shadow: -8px 0 32px rgba(0,0,0,0.3);
  overflow-y: auto;
  scrollbar-width: none;
}
.drawer-panel::-webkit-scrollbar { display: none; }

.drawer-body {
  position: relative;
  padding: 28px 24px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.drawer-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, background 0.2s;
  z-index: 10;
}
.drawer-close:hover {
  color: var(--jn-ink-strong);
  background: var(--jn-row-hover);
}

.head .eyebrow {
  margin: 0 0 6px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  text-transform: uppercase;
}
.head h3 {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-weight: 500;
  font-style: italic;
  font-size: 28px;
  color: var(--jn-ink-strong);
  line-height: 1.05;
}
.head .lead {
  margin: 10px 0 0;
  color: var(--jn-ink-dim);
  font-size: 13.5px;
  line-height: 1.55;
}

/* Status card */
.status-card {
  padding: 14px 16px;
  border: 1px solid var(--jn-hair);
  border-radius: 12px;
  background: var(--jn-row-hover);
}
.status-line { display: flex; align-items: center; gap: 10px; }
.status-line .label { flex: 1; font-size: 13px; color: var(--jn-ink); }
.tooltip-wrapper { position: relative; }
.tooltip-sm {
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

.refresh {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 0.15s;
}
.refresh:hover { color: var(--jn-ink-strong); }
.spinning { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.beacon {
  width: 10px; height: 10px; border-radius: 50%;
  background: var(--jn-ink-muted);
}
.beacon.state-ok { background: var(--jn-accent); box-shadow: 0 0 0 4px var(--jn-accent-soft); }
.beacon.state-off { background: var(--jn-danger); box-shadow: 0 0 0 4px color-mix(in oklab, var(--jn-danger) 22%, transparent); }
.uid {
  margin: 8px 0 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  letter-spacing: 0.02em;
}

/* Mode switch */
.mode-switch {
  display: inline-flex;
  border: 1px solid var(--jn-hair);
  border-radius: 8px;
  overflow: hidden;
  align-self: flex-start;
}
.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s, color 0.15s;
}
.mode-btn:hover { background: var(--jn-row-hover); }
.mode-btn.active {
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
}

/* Form */
.form { display: flex; flex-direction: column; gap: 14px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-dim);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.hint { font-size: 11.5px; color: var(--jn-ink-muted); }

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--jn-hair);
  border-radius: 999px;
  background: var(--jn-input-bg);
  transition: border-color 0.15s;
}
.input-wrapper:focus-within { border-color: var(--jn-accent); }
.input-wrapper .el-icon { color: var(--jn-ink-dim); flex-shrink: 0; }

.input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--jn-ink);
  font-size: 13px;
}
.input::placeholder { color: var(--jn-ink-muted); }

.textarea {
  padding: 10px 12px;
  border: 1px solid var(--jn-hair);
  border-radius: 8px;
  background: var(--jn-input-bg);
  color: var(--jn-ink);
  font-size: 13px;
  outline: none;
  resize: vertical;
  transition: border-color 0.15s;
}
.textarea:focus { border-color: var(--jn-accent); }
.textarea::placeholder { color: var(--jn-ink-muted); }

.submit {
  align-self: flex-start;
  padding: 10px 22px;
  border: none;
  border-radius: 999px;
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 10px 24px var(--jn-glow);
  transition: transform 0.15s, box-shadow 0.15s;
}
.submit:hover:not(:disabled) {
  transform: scale(1.04);
  box-shadow: 0 14px 32px var(--jn-glow);
}
.submit:active:not(:disabled) {
  transform: scale(0.96);
}
.submit:disabled {
  opacity: 0.5;
  cursor: default;
}

.footnote {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--jn-ink-muted);
  line-height: 1.55;
}

@media (max-width: 720px) {
  .lanzou-pill .txt { display: none; }
  .lanzou-pill { padding: 0 10px; }
  .drawer-panel { width: 92vw; max-width: 420px; }
}
</style>
