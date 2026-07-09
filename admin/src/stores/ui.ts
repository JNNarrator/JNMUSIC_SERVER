import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const showPlayerPage = ref(false)

  function openPlayerPage() {
    showPlayerPage.value = true
  }

  function closePlayerPage() {
    showPlayerPage.value = false
  }

  return { showPlayerPage, openPlayerPage, closePlayerPage }
})
