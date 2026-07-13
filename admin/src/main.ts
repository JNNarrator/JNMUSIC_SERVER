import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles.css'

// === 车机模式 ===
// URL 参数 ?car=1 进入，localStorage 持久化
const CAR_KEY = 'jnmusic.car-mode'
if (new URLSearchParams(location.search).has('car')) {
  localStorage.setItem(CAR_KEY, '1')
} else if (localStorage.getItem(CAR_KEY) === '1') {
  // persist across sessions
}
if (localStorage.getItem(CAR_KEY) === '1') {
  document.documentElement.classList.add('car-mode')
}
if (new URLSearchParams(location.search).has('no-car')) {
  localStorage.removeItem(CAR_KEY)
  document.documentElement.classList.remove('car-mode')
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
app.use(ElementPlus)
app.mount('#app')
