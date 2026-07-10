<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElDrawer,
  ElButton,
  ElInput,
  ElRadioGroup,
  ElRadioButton,
  ElIcon,
  ElTooltip,
  ElMessage,
} from 'element-plus'
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
  if (!cookie.value.trim()) { ElMessage.warning('请先粘贴 Cookie'); return }
  submitting.value = true
  try {
    const res = await fetch('/music/api/v1/admin/lanzou/cookie', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cookie: cookie.value.trim() }),
    })
    const p = await res.json()
    if (p.success) { status.value = p.data; cookie.value = ''; ElMessage.success('Cookie 已生效') }
    else ElMessage.error(p.error?.message || 'Cookie 无效')
  } catch (e) { ElMessage.error('网络异常') } finally { submitting.value = false }
}

async function submitLogin() {
  if (!username.value.trim() || !password.value) { ElMessage.warning('请填写账号与密码'); return }
  submitting.value = true
  try {
    const res = await fetch('/music/api/v1/admin/lanzou/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    })
    const p = await res.json()
    if (p.success) { status.value = p.data; password.value = ''; ElMessage.success('登录成功') }
    else ElMessage.error(p.error?.message || '登录失败')
  } catch (e) { ElMessage.error('网络异常') } finally { submitting.value = false }
}

function toggleOpen() { open.value = !open.value }
watch(open, (v) => { if (v) fetchStatus() })
fetchStatus()
</script>

<template>
  <el-tooltip :content="lightLabel" placement="bottom" :hide-after="800">
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
  </el-tooltip>

  <el-drawer
    v-model="open"
    direction="rtl"
    size="420px"
    :with-header="false"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    class="lanzou-drawer"
  >
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
          <el-tooltip content="重新校验" placement="top" :hide-after="800">
            <el-button class="refresh" circle text :loading="loading" @click="fetchStatus">
              <el-icon :size="15"><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
        <p v-if="status?.authenticated" class="uid">UID · {{ status.uid }}</p>
      </section>

      <el-radio-group v-model="mode" class="mode-switch" size="default">
        <el-radio-button value="cookie">
          <el-icon><Link /></el-icon>Cookie
        </el-radio-button>
        <el-radio-button value="password">
          <el-icon><Key /></el-icon>账号密码
        </el-radio-button>
      </el-radio-group>

      <form v-if="mode === 'cookie'" class="form" @submit.prevent="submitCookie">
        <label class="field">
          <span class="field-label">粘贴浏览器 Cookie</span>
          <el-input
            v-model="cookie"
            type="textarea"
            :rows="6"
            placeholder="phpdisk_info=...; ylogin=...; ..."
            resize="none"
          />
          <span class="hint">从 pc.woozooo.com 登录后复制完整 Cookie 字符串。</span>
        </label>
        <el-button class="submit" type="primary" round native-type="submit" :loading="submitting">
          写入并校验
        </el-button>
      </form>

      <form v-else class="form" @submit.prevent="submitLogin">
        <label class="field">
          <span class="field-label">账号</span>
          <el-input v-model="username" autocomplete="username" placeholder="蓝奏云账号" />
        </label>
        <label class="field">
          <span class="field-label">密码</span>
          <el-input
            v-model="password"
            type="password"
            autocomplete="current-password"
            show-password
            placeholder="蓝奏云密码"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </label>
        <el-button class="submit" type="primary" round native-type="submit" :loading="submitting">
          登录并校验
        </el-button>
      </form>

      <p class="footnote">凭据仅保存在服务端 Cookie 缓存中；重启后自动复用，直至蓝奏云失效。</p>
    </div>
  </el-drawer>
</template>

<style scoped>
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

.dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--jn-ink-muted);
  box-shadow: 0 0 0 3px transparent;
  transition: background 0.25s ease, box-shadow 0.25s ease;
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

.drawer-body { display: flex; flex-direction: column; gap: 20px; padding: 28px 24px 24px; color: var(--jn-ink); position: relative; min-height: 100%; }

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

.status-card {
  padding: 14px 16px;
  border: 1px solid var(--jn-hair);
  border-radius: 12px;
  background: var(--jn-row-hover);
}
.status-line { display: flex; align-items: center; gap: 10px; }
.status-line .label { flex: 1; font-size: 13px; color: var(--jn-ink); }
.status-line .refresh { color: var(--jn-ink-dim) !important; }
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

.mode-switch { align-self: flex-start; }
.mode-switch :deep(.el-radio-button__inner) {
  display: inline-flex; align-items: center; gap: 6px;
  border-color: var(--jn-hair) !important;
  background: transparent !important;
  color: var(--jn-ink-dim) !important;
}
.mode-switch :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: var(--jn-accent) !important;
  border-color: var(--jn-accent) !important;
  color: var(--jn-accent-ink) !important;
  box-shadow: -1px 0 0 0 var(--jn-accent) !important;
}

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

.submit {
  align-self: flex-start;
  color: var(--jn-accent-ink) !important;
  font-weight: 600 !important;
  padding: 10px 22px !important;
  box-shadow: 0 10px 24px var(--jn-glow);
}

.footnote {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--jn-ink-muted);
  line-height: 1.55;
}

.lanzou-drawer :deep(.el-drawer) {
  background: var(--jn-bg-elev) !important;
  border-left: 1px solid var(--jn-hair);
}
.lanzou-drawer :deep(.el-drawer__body) { padding: 0; }
.lanzou-drawer :deep(.el-textarea__inner) {
  border-radius: 8px !important;
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

@media (max-width: 720px) {
  .lanzou-pill .txt { display: none; }
  .lanzou-pill { padding: 0 10px; }
  .lanzou-drawer :deep(.el-drawer) { width: 92vw !important; max-width: 420px; }
}
</style>
