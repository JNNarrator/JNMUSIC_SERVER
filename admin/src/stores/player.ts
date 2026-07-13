import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { fetchLyricsCached } from '../utils/lrc'

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

// 用于取消上一次未完成的切歌就绪回调
let pendingReady: (() => void) | null = null

// 前端直链缓存：trackId -> { url, format, expiresAt }
const urlCache = new Map<string, { url: string; format: string; expiresAt: number }>()

async function fetchMediaUrl(trackId: string): Promise<{ url: string; format: string } | null> {
  // 先查缓存
  const cached = urlCache.get(trackId)
  if (cached && Date.now() < cached.expiresAt) return { url: cached.url, format: cached.format }
  try {
    const res = await fetch(`/music/api/v1/tracks/${trackId}/media-url`)
    const payload = await res.json()
    if (!payload.success || !payload.data?.mediaUrl) return null
    const expiresAt = payload.data.expiresAt
      ? new Date(payload.data.expiresAt).getTime()
      : Date.now() + 3.5 * 60 * 60 * 1000
    urlCache.set(trackId, { url: payload.data.mediaUrl, format: payload.data.format || '', expiresAt })
    return { url: payload.data.mediaUrl, format: payload.data.format || '' }
  } catch {
    return null
  }
}

// 批量获取直链
async function fetchMediaUrls(trackIds: string[]): Promise<Map<string, { url: string; format: string }>> {
  const result = new Map<string, { url: string; format: string }>()
  
  // 过滤出需要请求的ID（缓存中没有或已过期）
  const idsToFetch: string[] = []
  for (const id of trackIds) {
    const cached = urlCache.get(id)
    if (cached && Date.now() < cached.expiresAt) {
      result.set(id, { url: cached.url, format: cached.format })
    } else {
      idsToFetch.push(id)
    }
  }
  
  if (idsToFetch.length === 0) return result
  
  try {
    const res = await fetch(`/music/api/v1/tracks/media-urls?ids=${idsToFetch.join(',')}`)
    const payload = await res.json()
    if (payload.success && payload.data) {
      for (const [trackId, data] of Object.entries(payload.data)) {
        const mediaData = data as { mediaUrl: string; format?: string; expiresAt?: string }
        if (mediaData.mediaUrl) {
          const expiresAt = mediaData.expiresAt
            ? new Date(mediaData.expiresAt).getTime()
            : Date.now() + 3.5 * 60 * 60 * 1000
          urlCache.set(trackId, { url: mediaData.mediaUrl, format: mediaData.format || '', expiresAt })
          result.set(trackId, { url: mediaData.mediaUrl, format: mediaData.format || '' })
        }
      }
    }
  } catch {
    // 批量获取失败，回退到单个获取
    for (const id of idsToFetch) {
      const singleResult = await fetchMediaUrl(id)
      if (singleResult) {
        result.set(id, singleResult)
      }
    }
  }
  
  return result
}

async function prefetchNextUrls(tracks: Track[], currentIdx: number) {
  // 预取接下来3首的直链
  const prefetchCount = 3
  const idsToPrefetch: string[] = []
  
  for (let i = 1; i <= prefetchCount; i++) {
    const idx = (currentIdx + i) % tracks.length
    const track = tracks[idx]
    if (track?.trackId && !track.mediaUrl) {
      idsToPrefetch.push(track.trackId)
    }
  }
  
  if (idsToPrefetch.length === 0) return
  
  const urlMap = await fetchMediaUrls(idsToPrefetch)
  
  // 回写到队列
  for (let i = 1; i <= prefetchCount; i++) {
    const idx = (currentIdx + i) % tracks.length
    const track = tracks[idx]
    if (track?.trackId) {
      const urlData = urlMap.get(track.trackId)
      if (urlData) {
        tracks[idx] = { ...tracks[idx], mediaUrl: urlData.url }
      }
    }
  }
}


function updateMediaSession(track: Track) {
  if (!('mediaSession' in navigator)) return
  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.name,
    artist: track.artist,
    artwork: [{ src: '/music/favicon.svg', sizes: '512x512', type: 'image/svg+xml' }]
  })
}

export const usePlayerStore = defineStore('player', () => {
  const queue = ref<Track[]>([])
  const currentIndex = ref(-1)
  const isPlaying = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(0.85)
  const mode = ref<PlayMode>(readInitialMode())
  const loading = ref(false)
  let needsResume = false  // 后台切歌后需前台恢复播放

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

  function doPlay(url: string) {
    if (!audio) return
    // 取消上一次未完成的切歌回调，防止快速连点时状态混乱
    if (pendingReady) {
      audio.removeEventListener('canplay', pendingReady)
      pendingReady = null
    }
    loading.value = true
    isPlaying.value = false
    // 彻底终止并重置音频元素，避免旧音频残留
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
    // 切换到新源
    audio.src = url
    audio.currentTime = 0
    audio.volume = volume.value

    // 尝试播放（含锁屏重试）
    // 注意：不在这里设 isPlaying，由 play/pause 事件监听器管理，
    // 避免锁屏下 play() resolve 但无声时误显示播放状态
    const tryPlay = () => {
      loading.value = false
      const p = audio.play()
      if (p && typeof p.then === 'function') {
        p.then(() => {
          // play() resolve 了，但检查音频是否真的在播放
          // 锁屏下 iOS 可能 resolve 但不输出声音
          if (audio.paused) {
            isPlaying.value = false
          }
          // 如果没 pause，play 事件会设 isPlaying = true
        }).catch(() => {
          // 锁屏或后台模式下播放可能被拒绝，延迟重试一次
          setTimeout(() => {
            audio.play().then(() => {
              if (audio.paused) isPlaying.value = false
            }).catch(() => {
              isPlaying.value = false
            })
          }, 600)
        })
      }
    }

    // 等待缓冲就绪后播放，同时设超时兜底（锁屏下 canplay 可能延迟较大）
    let played = false
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    const onReady = () => {
      if (played) return
      played = true
      if (timeoutId) clearTimeout(timeoutId)
      audio.removeEventListener('canplay', onReady)
      pendingReady = null
      tryPlay()
    }

    pendingReady = onReady
    audio.addEventListener('canplay', onReady)

    // 超时兜底：3 秒后无论如何尝试播放（处理锁屏/后台场景）
    timeoutId = setTimeout(() => {
      if (!played) {
        played = true
        audio.removeEventListener('canplay', onReady)
        pendingReady = null
        tryPlay()
      }
    }, 3000)
  }

  async function playIndex(index: number) {
    if (!audio) return
    if (index < 0 || index >= queue.value.length) return
    currentIndex.value = index
    const track = queue.value[index]
    if (!track) return

    if (track.mediaUrl) {
      doPlay(track.mediaUrl)
      // 更新 Media Session 元数据（锁屏/控制中心显示）
      updateMediaSession(track)
      // 后台预取下一首 + 预加载当前歌词
      prefetchNextUrls(queue.value, index)
      fetchLyricsCached(track.trackId)
      return
    }

    // 没有直链，按需获取
    loading.value = true

    // 立即停止旧音频并清空源，防止旧歌在加载期间继续播放
    audio.pause()
    audio.src = ''
    // 锁定用户手势：play 会失败（无源）但仍会标记音频元素为已激活
    audio.play().catch(() => {})
    const result = await fetchMediaUrl(track.trackId)
    if (!result) {
      isPlaying.value = false
      loading.value = false
      return
    }
    // 回写到 queue 中，后续切回这首歌不再请求
    queue.value = queue.value.map((t, i) => i === index ? { ...t, mediaUrl: result.url } : t)
    doPlay(result.url)
    // 更新 Media Session 元数据（锁屏/控制中心显示）
    updateMediaSession(track)
    // 后台预取下一首 + 预加载当前歌词
    prefetchNextUrls(queue.value, index)
    fetchLyricsCached(track.trackId)
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
    // Media Session API: 让 iOS 在锁屏/后台识别为媒体应用，保持音频会话活跃
    if ('mediaSession' in navigator) {
      // play 必须永远尝试播放（不 toggle），解决锁屏下 audio 僵尸状态
      // 用户按锁屏/方向盘播放键时强制重连音频会话
      navigator.mediaSession.setActionHandler('play', () => {
        if (currentTrack.value) {
          audio.play().catch(() => {})
        }
      })
      navigator.mediaSession.setActionHandler('pause', () => {
        if (!audio.paused) audio.pause()
      })
      navigator.mediaSession.setActionHandler('previoustrack', () => {
        prev()
        updateMediaSession(currentTrack.value!)
        audio.play().catch(() => {})
      })
      navigator.mediaSession.setActionHandler('nexttrack', () => {
        next(true)
        updateMediaSession(currentTrack.value!)
        audio.play().catch(() => {})
      })
    }

    audio.addEventListener('play', () => {
      isPlaying.value = true
    })
    audio.addEventListener('pause', () => {
      isPlaying.value = false
    })
    audio.addEventListener('timeupdate', () => {
      currentTime.value = audio.currentTime
      // 更新 Media Session 播放位置，保持音频会话活跃
      if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
        try {
          navigator.mediaSession.setPositionState({
            duration: audio.duration || 0,
            playbackRate: audio.playbackRate || 1,
            position: audio.currentTime || 0,
          })
        } catch {}
      }
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
      // 锁屏/后台切歌标记：iOS 可能接受 play() 但不输出声音
      if (document.hidden) {
        needsResume = true
      }
    })
    audio.addEventListener('error', () => {
      loading.value = false
      isPlaying.value = false
    })

    // iOS PWA 后台切歌恢复：回到前台时若需要恢复播放，pause+play 重连音频输出
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && needsResume && currentTrack.value) {
        needsResume = false
        // 短暂暂停再播放，强制 iOS 重连音频会话
        audio.pause()
        setTimeout(() => {
          audio.play().catch(() => {})
        }, 50)
      }
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
