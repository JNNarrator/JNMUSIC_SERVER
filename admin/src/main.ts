import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles.css'

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
