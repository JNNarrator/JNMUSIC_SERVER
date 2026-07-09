import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type Track = {
  trackId: string
  name: string
  artist: string
  format?: string
  fileSize?: number
  mediaUrl?: string
  urlExpiresAt?: string
}

// 三态播放模式：列表循环 -> 单曲循环 -> 随机播放 -> 列表循环。
export type PlayMode = 'list' | 'one' | 'shuffle'

const MODE_STORAGE_KEY = 'player.mode'
const VOLUME_STORAGE_KEY = 'player.volume'
const MODE_ORDER: PlayMode[] = ['list', 'one', 'shuffle']

function readInitialMode(): PlayMode {
  if (typeof window === 'undefined') return 'list'
  const saved = window.localStorage.getItem(MODE_STORAGE_KEY)
  if (saved === 'list' || saved === 'one' || saved === 'shuffle') return saved
  return 'list'
}

// 单例音频对象：Vue 组件卸载不影响播放，符合桌面/移动端持久播放心理预期。
const audio: HTMLAudioElement | null =
  typeof window !== 'undefined' ? new Audio() : null
if (audio) audio.preload = 'metadata'

export const usePlayerStore = defineStore('player', () => {
  const queue = ref<Track[]>([])
  const currentIndex = ref(-1)
  const isPlaying = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(0.85)
  const mode = ref<PlayMode>(readInitialMode())
  const loading = ref(false)

  const currentTrack = computed(() =>
    currentIndex.value >= 0 ? queue.value[currentIndex.value] : null
  )

  function setQueue(tracks: Track[], startIndex = 0) {
    queue.value = tracks
    if (!tracks.length) {
      currentIndex.value = -1
      stop()
      return
    }
    playIndex(Math.min(startIndex, tracks.length - 1))
  }

  function playIndex(index: number) {
    if (!audio) return
    if (index < 0 || index >= queue.value.length) return
    currentIndex.value = index
    const track = queue.value[index]
    if (!track?.mediaUrl) {
      isPlaying.value = false
      return
    }
    loading.value = true
    audio.src = track.mediaUrl
    audio.currentTime = 0
    audio.volume = volume.value
    const promise = audio.play()
    if (promise && typeof promise.catch === 'function') {
      promise.catch(() => {
        // 浏览器自动播放策略拒绝时，保持暂停态由用户手动触发。
        isPlaying.value = false
        loading.value = false
      })
    }
  }

  function toggle() {
    if (!audio) return
    if (!currentTrack.value) {
      if (queue.value.length) playIndex(0)
      return
    }
    if (audio.paused) {
      audio.play().catch(() => {})
    } else {
      audio.pause()
    }
  }

  function stop() {
    if (!audio) return
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
    isPlaying.value = false
    currentTime.value = 0
    duration.value = 0
  }

  function pickShuffleIndex(): number {
    if (queue.value.length <= 1) return 0
    let next = currentIndex.value
    while (next === currentIndex.value) {
      next = Math.floor(Math.random() * queue.value.length)
    }
    return next
  }

  function next(userTriggered = true) {
    if (!queue.value.length) return
    // 单曲循环仅在自然结束时生效，用户手动切歌应正常前进。
    if (!userTriggered && mode.value === 'one') {
      playIndex(currentIndex.value)
      return
    }
    if (mode.value === 'shuffle') {
      playIndex(pickShuffleIndex())
      return
    }
    const last = queue.value.length - 1
    if (currentIndex.value >= last) {
      // list 模式与手动下一曲：到末尾回到开头。
      playIndex(0)
      return
    }
    playIndex(currentIndex.value + 1)
  }

  function prev() {
    if (!queue.value.length) return
    // 3 秒后按上一曲视作重播当前曲，符合主流播放器交互。
    if (audio && audio.currentTime > 3) {
      audio.currentTime = 0
      return
    }
    if (mode.value === 'shuffle') {
      playIndex(pickShuffleIndex())
      return
    }
    if (currentIndex.value <= 0) {
      playIndex(queue.value.length - 1)
      return
    }
    playIndex(currentIndex.value - 1)
  }

  function seek(seconds: number) {
    if (!audio) return
    audio.currentTime = Math.max(0, Math.min(seconds, duration.value || seconds))
  }

  function setVolume(value: number) {
    volume.value = Math.max(0, Math.min(1, value))
    if (audio) audio.volume = volume.value
  }

  function cyclePlayMode() {
    mode.value = MODE_ORDER[(MODE_ORDER.indexOf(mode.value) + 1) % MODE_ORDER.length]
  }

  function setPlayMode(next: PlayMode) {
    mode.value = next
  }

  if (audio) {
    audio.addEventListener('play', () => {
      isPlaying.value = true
    })
    audio.addEventListener('pause', () => {
      isPlaying.value = false
    })
    audio.addEventListener('timeupdate', () => {
      currentTime.value = audio.currentTime
    })
    audio.addEventListener('loadedmetadata', () => {
      duration.value = audio.duration || 0
      loading.value = false
    })
    audio.addEventListener('canplay', () => {
      loading.value = false
    })
    audio.addEventListener('waiting', () => {
      loading.value = true
    })
    audio.addEventListener('ended', () => {
      next(false)
    })
    audio.addEventListener('error', () => {
      loading.value = false
      isPlaying.value = false
    })
  }

  // 音量持久化，避免刷新后回到默认值影响耳机用户体验。
  const persisted = typeof window !== 'undefined'
    ? Number(window.localStorage.getItem(VOLUME_STORAGE_KEY))
    : NaN
  if (!Number.isNaN(persisted) && persisted > 0 && persisted <= 1) {
    setVolume(persisted)
  }
  watch(volume, (v) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VOLUME_STORAGE_KEY, String(v))
    }
  })
  watch(mode, (m) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(MODE_STORAGE_KEY, m)
    }
  })

  return {
    queue,
    currentIndex,
    currentTrack,
    isPlaying,
    currentTime,
    duration,
    volume,
    mode,
    loading,
    setQueue,
    playIndex,
    toggle,
    next,
    prev,
    seek,
    setVolume,
    cyclePlayMode,
    setPlayMode,
  }
})
