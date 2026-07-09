import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'jn.theme'

function detectInitial(): ThemeMode {
  if (typeof window === 'undefined') return 'dark'
  const saved = window.localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return 'dark'
}

function apply(mode: ThemeMode) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', mode)
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(detectInitial())
  apply(mode.value)

  watch(mode, (m) => {
    apply(m)
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, m)
    }
  })

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
  }

  function set(next: ThemeMode) {
    mode.value = next
  }

  return { mode, toggle, set }
})
