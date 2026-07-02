import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAdminStore = defineStore('admin', () => {
  const token = ref(localStorage.getItem('admin_token') ?? '')
  const username = ref(localStorage.getItem('admin_username') ?? '')

  function login(nextToken, nextUsername) {
    token.value = nextToken
    username.value = nextUsername
    localStorage.setItem('admin_token', nextToken)
    localStorage.setItem('admin_username', nextUsername)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
  }

  return { token, username, login, logout }
})
