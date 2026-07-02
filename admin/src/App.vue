<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAdminStore } from './stores/admin'

const admin = useAdminStore()
const currentView = computed(() => admin.token ? 'track' : 'login')
</script>

<template>
  <div class="app-shell">
    <LoginView v-if="currentView === 'login'" />
    <TrackListView v-else />
  </div>
</template>

<style>
:global(body) {
  margin: 0;
}

.app-shell {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, #ff9a9e, transparent 45%),
    radial-gradient(circle at top right, #a18cd1, transparent 45%),
    linear-gradient(135deg, #fad0c4 0%, #ffd1ff 100%);
}
</style>
