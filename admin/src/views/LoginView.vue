<script setup lang="ts">
import { reactive } from 'vue'
import { useAdminStore } from '../stores/admin'

const admin = useAdminStore()
const form = reactive({ username: 'jiangnan', password: 'jiangnan123', error: '', loading: false })

async function handleLogin() {
  form.error = ''
  form.loading = true
  try {
    const response = await fetch('/music/api/v1/admin/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: form.username, password: form.password })
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok || !payload.success) {
      form.error = payload?.error?.message || '登录失败，请重试'
      return
    }
    admin.login(payload.data.token, payload.data.username)
  } catch (error) {
    form.error = '网络异常，请稍后重试'
  } finally {
    form.loading = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="glass-card login-card">
      <p class="brand">🎵 JNMusic</p>
      <h1>管理后台登录</h1>
      <form @submit.prevent="handleLogin">
        <label>
          <span>账号</span>
          <input v-model="form.username" autocomplete="username" placeholder="请输入账号" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <p v-if="form.error" class="error">{{ form.error }}</p>
        <button :disabled="form.loading" type="submit">{{ form.loading ? '登录中...' : '登录' }}</button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
}

.login-card {
  width: min(380px, 100%);
  padding: 28px 26px 26px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 30px 80px rgba(120, 90, 160, 0.25);
  backdrop-filter: blur(18px) saturate(140%);
}

h1 {
  margin: 10px 0 22px;
  font-size: 24px;
  color: #522d6b;
}

.brand {
  margin: 0;
  font-size: 32px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
  font-size: 15px;
  color: #663a7a;
}

label span {
  font-weight: 600;
}

input {
  border: none;
  border-radius: 18px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 2px 6px rgba(90, 50, 110, 0.08);
  font-size: 15px;
  color: #3a2a45;
}

button {
  margin-top: 8px;
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 18px;
  background: linear-gradient(135deg, #ff9a9e, #a18cd1);
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: transform .15s ease, box-shadow .15s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 18px 35px rgba(160, 120, 200, 0.35);
}

button:disabled {
  opacity: 0.85;
  cursor: not-allowed;
}

.error {
  color: #d6336c;
  margin: 8px 0 0;
  font-size: 14px;
}
</style>
