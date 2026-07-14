import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles.css'

// === 车机模式：URL 参数 ?car=1 强制，或根据 UA/屏幕宽高比自动检测 ===
const isCar = new URLSearchParams(location.search).has('car')
  || /car|pad|tablet|flyme|android(?!.*mobile)/i.test(navigator.userAgent)
  || (screen.width >= 1024 && screen.width / screen.height > 1.5)

if (isCar) {
  document.documentElement.classList.add('car-mode')
}

// === Service Worker 注册（Android 离线缓存 + PWA 增强） ===
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch(() => {})
  })
}

// === PWA 安装引导（Android Chrome beforeinstallprompt） ===
let installPromptEvent: Event | null = null
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  installPromptEvent = e
  document.documentElement.classList.add('pwa-installable')
})
window.addEventListener('appinstalled', () => {
  installPromptEvent = null
  document.documentElement.classList.remove('pwa-installable')
})
;(window as any).__installPrompt = () => {
  if (installPromptEvent) {
    (installPromptEvent as any).prompt()
    installPromptEvent = null
    document.documentElement.classList.remove('pwa-installable')
  }
}

// iOS PWA 视口高度修正：100dvh 在 PWA standalone 模式下不等于物理屏幕高度
// iOS PWA 底部安全区域 JS 回退方案
const fixBottomBar = () => {
  const diff = window.screen.height - window.innerHeight
  if (diff > 0) {
    document.documentElement.style.setProperty('--safe-bottom', diff + 'px')
  }
}
window.addEventListener('resize', fixBottomBar)
window.addEventListener('load', fixBottomBar)


// Vant 样式
import 'vant/lib/index.css'

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
